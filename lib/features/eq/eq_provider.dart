import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

import '../../data/repositories/preferences_repository.dart';
import '../../data/services/audio_handler.dart';
import 'eq_model.dart';

/// An entry of the AutoEQ catalogue (searchable online).
class AutoEqEntry {
  final String name;
  final String source;
  final String path; // result directory under AutoEq/results
  const AutoEqEntry(this.name, this.source, this.path);
}

/// IEM equalizer: pick your IEM, Lyro applies its measured correction (from
/// AutoEQ) plus your own fine-tune, through a 32-band native EQ.
class EqProvider extends ChangeNotifier {
  final PreferencesRepository prefsRepo;
  final LyroAudioHandler audioHandler;
  static const _channel = MethodChannel('lyro/eq');
  static const _autoEqRaw = 'https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/';

  List<IemProfile> _bundled = [];
  List<IemProfile> _downloaded = [];
  bool _enabled = false;
  String _selectedId = 'sound|flat';
  List<double> _manual = List.filled(ManualBands.freqs.length, 0);
  bool _nativeAvailable = true;
  int? _sessionId;
  bool _attached = false; // native effect exists (only while enabled)
  StreamSubscription<int?>? _sessionSub;
  Timer? _applyDebounce;

  EqProvider({required this.prefsRepo, required this.audioHandler}) {
    _restore();
    _loadBundled();
    _sessionSub = audioHandler.player.androidAudioSessionIdStream.listen((id) {
      if (id == null || id == _sessionId) return;
      _sessionId = id;
      _attached = false;
      if (_enabled) _apply();
    });
  }

  // ---------------------------------------------------------------- state

  bool get enabled => _enabled;
  bool get nativeAvailable => _nativeAvailable;
  List<double> get manual => List.unmodifiable(_manual);
  List<IemProfile> get bundled => _bundled;
  List<IemProfile> get downloaded => _downloaded;

  IemProfile get selected =>
      [..._bundled, ..._downloaded, ...SoundProfiles.all].where((p) => p.id == _selectedId).firstOrNull ??
      SoundProfiles.all.first;

  /// Everything applied: the IEM correction plus the manual fine-tune.
  List<PeqFilter> get allFilters => [...selected.filters, ...ManualBands.filters(_manual)];
  List<double> get bandGains => EqCurve.bandGains(allFilters);
  double get preamp => EqCurve.preampFor(bandGains);

  Future<void> setEnabled(bool v) async {
    _enabled = v;
    await _changed();
  }

  Future<void> select(IemProfile p) async {
    _selectedId = p.id;
    _enabled = true;
    await _changed();
  }

  Future<void> setManual(int band, double db) async {
    _manual[band] = db.clamp(-ManualBands.maxDb, ManualBands.maxDb);
    await _changed(debounce: true);
  }

  Future<void> resetManual() async {
    _manual = List.filled(ManualBands.freqs.length, 0);
    await _changed();
  }

  Future<void> removeDownloaded(IemProfile p) async {
    _downloaded = _downloaded.where((x) => x.id != p.id).toList();
    if (_selectedId == p.id) _selectedId = 'sound|flat';
    await _changed();
  }

  Future<void> _changed({bool debounce = false}) async {
    notifyListeners();
    _save();
    _applyDebounce?.cancel();
    if (debounce) {
      _applyDebounce = Timer(const Duration(milliseconds: 60), _apply);
    } else {
      await _apply();
    }
  }

  // ---------------------------------------------------------------- persistence

  void _restore() {
    final raw = prefsRepo.getEqualizerJson();
    if (raw == null) return;
    try {
      final j = jsonDecode(raw) as Map<String, dynamic>;
      _enabled = j['enabled'] as bool? ?? false;
      _selectedId = j['selected'] as String? ?? _selectedId;
      final m = (j['manual'] as List?)?.map((e) => (e as num).toDouble()).toList();
      if (m != null && m.length == _manual.length) _manual = m;
      _downloaded = [
        for (final p in j['downloaded'] as List? ?? const []) IemProfile.fromJson(p as Map<String, dynamic>),
      ];
    } catch (_) {
      // corrupt state: start fresh
    }
  }

  void _save() {
    prefsRepo.setEqualizerJson(jsonEncode({
      'enabled': _enabled,
      'selected': _selectedId,
      'manual': _manual,
      'downloaded': [for (final p in _downloaded) p.toJson()],
    }));
  }

  Future<void> _loadBundled() async {
    try {
      final raw = await rootBundle.loadString('assets/eq/iem_profiles.json');
      final j = jsonDecode(raw) as Map<String, dynamic>;
      _bundled = [
        for (final p in j['profiles'] as List)
          IemProfile.fromJson(p as Map<String, dynamic>, id: 'bundled|${p['brand']}|${p['name']}'),
      ];
      notifyListeners();
      await _apply();
    } catch (e) {
      debugPrint('EQ profiles not loaded: $e');
    }
  }

  // ---------------------------------------------------------------- native engine

  /// The effect is only created while the EQ is on, so a disabled EQ never
  /// touches the audio path.
  Future<bool> _attach() async {
    final id = _sessionId;
    if (id == null) return false;
    try {
      final ok = await _channel.invokeMethod<bool>('attach', {'session': id});
      _nativeAvailable = ok ?? false;
      _attached = _nativeAvailable;
      notifyListeners();
    } on MissingPluginException {
      _nativeAvailable = false;
    } catch (e) {
      debugPrint('EQ attach failed: $e');
    }
    return _attached;
  }

  Future<void> _apply() async {
    try {
      if (!_enabled) {
        if (_attached) {
          _attached = false;
          await _channel.invokeMethod('release');
        }
        return;
      }
      if (!_attached && !await _attach()) return;
      final gains = bandGains;
      await _channel.invokeMethod('apply', {
        'enabled': true,
        'inputGain': EqCurve.preampFor(gains) - 0.5, // a little extra headroom
        'cutoffs': EqCurve.bandEdges,
        'centers': EqCurve.bandCenters,
        'gains': gains,
      });
    } on MissingPluginException {
      // tests / non-Android
    } catch (e) {
      debugPrint('EQ apply failed: $e');
    }
  }

  // ---------------------------------------------------------------- AutoEQ online

  List<AutoEqEntry>? _index;

  /// Searches the AutoEQ catalogue (index downloaded once, then cached).
  Future<List<AutoEqEntry>> searchOnline(String query) async {
    final q = query.trim().toLowerCase();
    if (q.length < 2) return const [];
    final index = _index ??= await _loadIndex();
    final words = q.split(RegExp(r'\s+'));
    return index.where((e) => words.every(e.name.toLowerCase().contains)).take(60).toList();
  }

  Future<List<AutoEqEntry>> _loadIndex() async {
    final dir = await getApplicationSupportDirectory();
    final cache = File('${dir.path}/autoeq_index.md');
    String text;
    final fresh = await cache.exists() &&
        DateTime.now().difference(await cache.lastModified()) < const Duration(days: 30);
    if (fresh) {
      text = await cache.readAsString();
    } else {
      text = await _get('${_autoEqRaw}INDEX.md');
      await cache.writeAsString(text);
    }
    final line = RegExp(r'^- \[(.+?)\]\(\./(.+?)\) by (.+)$', multiLine: true);
    return [
      for (final m in line.allMatches(text))
        AutoEqEntry(m.group(1)!, m.group(3)!.trim(), Uri.decodeFull(m.group(2)!)),
    ];
  }

  /// Downloads an AutoEQ result, keeps it on the device and selects it.
  Future<IemProfile> download(AutoEqEntry e) async {
    final leaf = e.path.split('/').last;
    final url = _autoEqRaw + Uri.encodeFull('${e.path}/$leaf ParametricEQ.txt');
    final text = await _get(url);
    final profile = IemProfile.parseAutoEq(
      text,
      id: 'autoeq|${e.path}',
      brand: '',
      name: e.name,
      source: e.source,
    );
    if (profile == null) throw const FormatException('No filters in that AutoEQ result');
    _downloaded = [..._downloaded.where((p) => p.id != profile.id), profile];
    await select(profile);
    return profile;
  }

  static Future<String> _get(String url) async {
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 12);
    try {
      final req = await client.getUrl(Uri.parse(url));
      final res = await req.close().timeout(const Duration(seconds: 20));
      final body = await res.transform(utf8.decoder).join();
      if (res.statusCode != 200) throw HttpException('HTTP ${res.statusCode}');
      return body;
    } finally {
      client.close(force: true);
    }
  }

  @override
  void dispose() {
    _sessionSub?.cancel();
    _applyDebounce?.cancel();
    super.dispose();
  }
}
