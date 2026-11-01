import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:on_audio_query/on_audio_query.dart';

import '../../features/player/widgets/artwork_cache.dart';
import '../models/song_model.dart';
import '../repositories/preferences_repository.dart';

/// Web stream: serves the music library to a browser on the same Wi-Fi.
///
/// The phone runs a small HTTP server; the laptop opens the address shown in
/// Profile and plays the files itself (so audio comes out of whatever is
/// connected to the laptop). A key in the link keeps others on the network
/// out, and a native foreground service keeps Wi-Fi awake meanwhile.
class WebStreamServer extends ChangeNotifier {
  final PreferencesRepository prefsRepo;

  /// The current library; read on every request so rescans show up.
  List<Song> Function() songs = () => const [];

  WebStreamServer({required this.prefsRepo});

  static const _channel = MethodChannel('lyro/stream');
  static const _ports = [8686, 8687, 8688, 8689];
  static const _cookie = 'lyro_k';

  HttpServer? _server;
  String? _host;
  String? _error;
  int _clients = 0;
  final Set<String> _seen = {};

  bool get running => _server != null;
  String? get error => _error;

  /// Browsers that have connected since the server started.
  int get clients => _clients;

  String get key {
    var k = prefsRepo.getWebStreamKey();
    if (k == null || k.length < 6) {
      const chars = 'abcdefghjkmnpqrstuvwxyz23456789';
      final rnd = Random.secure();
      k = List.generate(6, (_) => chars[rnd.nextInt(chars.length)]).join();
      prefsRepo.setWebStreamKey(k);
    }
    return k;
  }

  /// Address to type on the laptop (without the key).
  String? get address => running && _host != null ? 'http://$_host:${_server!.port}' : null;

  /// Full link, key included.
  String? get link => address == null ? null : '$address/?k=$key';

  Future<void> start() async {
    if (running) return;
    _error = null;
    _host = await _wifiAddress();
    if (_host == null) {
      _error = 'Connect the phone to Wi-Fi first';
      notifyListeners();
      return;
    }
    for (final port in _ports) {
      try {
        _server = await HttpServer.bind(InternetAddress.anyIPv4, port);
        break;
      } on SocketException {
        // try the next port
      }
    }
    if (_server == null) {
      _error = 'No free port for the web stream';
      notifyListeners();
      return;
    }
    _server!.autoCompress = false;
    _server!.listen(_handle, onError: (Object e) => debugPrint('Web stream: $e'));
    _clients = 0;
    _seen.clear();
    try {
      await _channel.invokeMethod('start', {'address': address});
    } on MissingPluginException {
      // tests / non-Android
    } catch (e) {
      debugPrint('Web stream service: $e');
    }
    notifyListeners();
  }

  Future<void> stop() async {
    final server = _server;
    _server = null;
    await server?.close(force: true);
    try {
      await _channel.invokeMethod('stop');
    } on MissingPluginException {
      // tests / non-Android
    } catch (e) {
      debugPrint('Web stream service: $e');
    }
    notifyListeners();
  }

  /// A fresh key; anyone using the old link is locked out.
  Future<void> newKey() async {
    await prefsRepo.setWebStreamKey('');
    key;
    notifyListeners();
  }

  static Future<String?> _wifiAddress() async {
    final interfaces = await NetworkInterface.list(type: InternetAddressType.IPv4);
    String? fallback;
    for (final i in interfaces) {
      for (final a in i.addresses) {
        if (a.isLoopback || a.isLinkLocal) continue;
        if (i.name.startsWith('wlan') || i.name.startsWith('ap') || i.name.startsWith('swlan')) return a.address;
        if (!i.name.startsWith('rmnet') && !i.name.startsWith('ccmni')) fallback ??= a.address;
      }
    }
    return fallback;
  }

  // ---------------------------------------------------------------- requests

  Future<void> _handle(HttpRequest req) async {
    final res = req.response;
    try {
      final path = req.uri.path;
      final queryKey = req.uri.queryParameters['k'];
      final authed = queryKey == key || req.cookies.any((c) => c.name == _cookie && c.value == key);
      if (queryKey == key) {
        res.cookies.add(Cookie(_cookie, key)
          ..path = '/'
          ..httpOnly = true
          ..maxAge = 60 * 60 * 24 * 365);
      }

      if (path == '/' || path == '/index.html') return await _asset(res, 'assets/web/index.html', 'text/html; charset=utf-8');
      if (path.startsWith('/fonts/')) return await _font(res, path.substring(7));
      if (!authed) return _status(res, HttpStatus.unauthorized);

      final ip = req.connectionInfo?.remoteAddress.address;
      if (ip != null && _seen.add(ip)) {
        _clients = _seen.length;
        notifyListeners();
      }

      if (path == '/api/library') return _library(res);
      final id = int.tryParse(path.split('/').last);
      if (id != null && path.startsWith('/api/art/')) return await _art(res, id);
      if (id != null && path.startsWith('/api/stream/')) return await _stream(req, res, id);
      _status(res, HttpStatus.notFound);
    } catch (e) {
      debugPrint('Web stream request failed: $e');
      try {
        res.statusCode = HttpStatus.internalServerError;
      } catch (_) {
        // headers already sent
      }
    } finally {
      await res.close().catchError((_) => null);
    }
  }

  void _status(HttpResponse res, int code) => res.statusCode = code;

  Future<void> _asset(HttpResponse res, String asset, String type, {String cache = 'no-cache'}) async {
    final data = await rootBundle.load(asset);
    res.headers.set(HttpHeaders.contentTypeHeader, type);
    res.headers.set(HttpHeaders.cacheControlHeader, cache);
    res.add(data.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes));
  }

  static const _fonts = {
    'doto.ttf': 'assets/fonts/Doto-ROND-wght.ttf',
    'geist.ttf': 'assets/fonts/Geist-Regular.ttf',
    'geist-semibold.ttf': 'assets/fonts/Geist-SemiBold.ttf',
    'geistmono.ttf': 'assets/fonts/GeistMono-Regular.ttf',
  };

  Future<void> _font(HttpResponse res, String name) async {
    final asset = _fonts[name];
    if (asset == null) return _status(res, HttpStatus.notFound);
    await _asset(res, asset, 'font/ttf', cache: 'max-age=604800');
  }

  Song? _song(int id) => songs().where((s) => s.id == id && s.path.isNotEmpty).firstOrNull;

  void _library(HttpResponse res) {
    final list = [
      for (final s in songs())
        if (s.path.isNotEmpty)
          {
            'id': s.id,
            't': s.title,
            'a': s.artist,
            'al': s.album,
            'd': s.duration,
            'f': s.format,
            'n': s.trackNumber,
            'dir': s.path.substring(0, max(0, s.path.lastIndexOf('/'))).split('/').last,
            'add': s.dateAdded,
          },
    ];
    res.headers.contentType = ContentType.json;
    res.headers.set(HttpHeaders.cacheControlHeader, 'no-store');
    res.write(jsonEncode({'songs': list}));
  }

  Future<void> _art(HttpResponse res, int id) async {
    final bytes = await ArtworkCache.load(id, ArtworkType.AUDIO, 360);
    if (bytes == null || bytes.isEmpty) return _status(res, HttpStatus.notFound);
    final png = bytes.length > 4 && bytes[0] == 0x89 && bytes[1] == 0x50;
    res.headers.set(HttpHeaders.contentTypeHeader, png ? 'image/png' : 'image/jpeg');
    res.headers.set(HttpHeaders.cacheControlHeader, 'private, max-age=86400');
    res.add(bytes);
  }

  static const _types = {
    'mp3': 'audio/mpeg',
    'flac': 'audio/flac',
    'wav': 'audio/wav',
    'm4a': 'audio/mp4',
    'mp4': 'audio/mp4',
    'aac': 'audio/aac',
    'ogg': 'audio/ogg',
    'oga': 'audio/ogg',
    'opus': 'audio/ogg',
    'webm': 'audio/webm',
  };

  /// Sends the file as-is (no re-encoding), with byte ranges for seeking.
  Future<void> _stream(HttpRequest req, HttpResponse res, int id) async {
    final song = _song(id);
    final file = song == null ? null : File(song.path);
    if (file == null || !await file.exists()) return _status(res, HttpStatus.notFound);
    final length = await file.length();
    final ext = song!.path.split('.').last.toLowerCase();
    res.headers.set(HttpHeaders.contentTypeHeader, _types[ext] ?? 'application/octet-stream');
    res.headers.set(HttpHeaders.acceptRangesHeader, 'bytes');
    res.headers.set(HttpHeaders.cacheControlHeader, 'private, max-age=3600');

    var start = 0;
    var end = length - 1;
    final range = RegExp(r'bytes=(\d*)-(\d*)').firstMatch(req.headers.value(HttpHeaders.rangeHeader) ?? '');
    if (range != null) {
      final from = range.group(1)!, to = range.group(2)!;
      if (from.isEmpty && to.isNotEmpty) {
        start = max(0, length - int.parse(to));
      } else if (from.isNotEmpty) {
        start = int.parse(from);
        if (to.isNotEmpty) end = min(int.parse(to), length - 1);
      }
      if (start >= length || start > end) {
        res.headers.set(HttpHeaders.contentRangeHeader, 'bytes */$length');
        return _status(res, HttpStatus.requestedRangeNotSatisfiable);
      }
      res.statusCode = HttpStatus.partialContent;
      res.headers.set(HttpHeaders.contentRangeHeader, 'bytes $start-$end/$length');
    }
    res.contentLength = end - start + 1;
    if (req.method == 'HEAD') return;
    try {
      await res.addStream(file.openRead(start, end + 1));
    } on HttpException {
      // the browser hung up (seek, skip): normal
    } on SocketException {
      // same
    }
  }

  @override
  void dispose() {
    _server?.close(force: true);
    super.dispose();
  }
}
