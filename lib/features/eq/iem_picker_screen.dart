import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_palette.dart';
import '../../core/theme/nothing_typography.dart';
import '../../shared/widgets/dot_toast.dart';
import '../library/providers/library_provider.dart';
import 'eq_model.dart';
import 'eq_provider.dart';

/// Pick your IEM: bundled measured models, ones you've downloaded, any model
/// in the AutoEQ catalogue (online search), or a general sound profile.
class IemPickerScreen extends StatefulWidget {
  const IemPickerScreen({super.key});

  @override
  State<IemPickerScreen> createState() => _IemPickerScreenState();
}

class _IemPickerScreenState extends State<IemPickerScreen> {
  final _query = TextEditingController();
  Timer? _debounce;
  List<AutoEqEntry> _online = const [];
  bool _searching = false;
  String? _searchError;
  String? _downloading;

  @override
  void dispose() {
    _debounce?.cancel();
    _query.dispose();
    super.dispose();
  }

  void _onQuery(String q) {
    setState(() {});
    _debounce?.cancel();
    if (!context.read<LibraryProvider>().onlineArtwork || q.trim().length < 2) {
      setState(() => _online = const []);
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 350), () async {
      setState(() {
        _searching = true;
        _searchError = null;
      });
      try {
        final results = await context.read<EqProvider>().searchOnline(q);
        if (mounted && q == _query.text) setState(() => _online = results);
      } catch (_) {
        if (mounted) setState(() => _searchError = 'Couldn\'t reach AutoEQ. Check your connection.');
      } finally {
        if (mounted) setState(() => _searching = false);
      }
    });
  }

  Future<void> _pick(IemProfile p) async {
    HapticFeedback.mediumImpact();
    await context.read<EqProvider>().select(p);
    if (mounted) {
      showDotToast(context, 'EQ · ${p.title}');
      Navigator.pop(context);
    }
  }

  Future<void> _download(AutoEqEntry e) async {
    setState(() => _downloading = e.path);
    try {
      final p = await context.read<EqProvider>().download(e);
      if (mounted) {
        HapticFeedback.mediumImpact();
        showDotToast(context, 'EQ · ${p.title}');
        Navigator.pop(context);
      }
    } catch (_) {
      if (mounted) showDotToast(context, 'Download failed');
    } finally {
      if (mounted) setState(() => _downloading = null);
    }
  }

  bool _matches(IemProfile p) {
    final q = _query.text.trim().toLowerCase();
    if (q.isEmpty) return true;
    return q.split(RegExp(r'\s+')).every(p.title.toLowerCase().contains);
  }

  @override
  Widget build(BuildContext context) {
    final c = NothingPalette.of(context);
    final eq = context.watch<EqProvider>();
    final online = context.watch<LibraryProvider>().onlineArtwork;
    final bundled = eq.bundled.where(_matches).toList();
    final downloaded = eq.downloaded.where(_matches).toList();
    final sounds = SoundProfiles.all.where(_matches).toList();
    final q = _query.text.trim();

    Widget header(String t) => Padding(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 6),
          child: Text(t, style: NothingTypography.monoLabel(isDark: c.dark)),
        );

    Widget tile(IemProfile p, {Widget? trailing}) {
      final selected = p.id == eq.selected.id;
      return ListTile(
        key: Key('iem_${p.id}'),
        contentPadding: const EdgeInsets.symmetric(horizontal: 20),
        title: Text(
          p.title,
          style: TextStyle(
            fontFamily: NothingTypography.fontUi,
            fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
            color: c.display,
          ),
        ),
        subtitle: Text(
          [
            if (p.measured) 'by ${p.source}',
            ?p.note,
          ].join(' · '),
          style: TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 12, color: c.secondary),
        ),
        trailing: selected
            ? const Icon(Icons.check_circle, color: NothingColors.signalRed)
            : trailing,
        onTap: () => _pick(p),
      );
    }

    return Scaffold(
      backgroundColor: c.bg,
      appBar: AppBar(
        backgroundColor: c.bg,
        title: Text('CHOOSE YOUR IEM', style: NothingTypography.displayTitle(isDark: c.dark, fontSize: 16)),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 4, 20, 4),
            child: TextField(
              key: const Key('iem_search'),
              controller: _query,
              onChanged: _onQuery,
              cursorColor: NothingColors.signalRed,
              style: TextStyle(fontFamily: NothingTypography.fontUi, color: c.display),
              decoration: InputDecoration(
                hintText: 'Search IEMs and headphones',
                prefixIcon: Icon(Icons.search, color: c.secondary),
                filled: true,
                fillColor: c.raised,
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
              ),
            ),
          ),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.only(bottom: 32),
              children: [
                if (bundled.isNotEmpty) ...[header('MEASURED · ON THIS PHONE'), ...bundled.map(tile)],
                if (downloaded.isNotEmpty) ...[
                  header('DOWNLOADED'),
                  for (final p in downloaded)
                    tile(
                      p,
                      trailing: IconButton(
                        tooltip: 'Remove',
                        icon: Icon(Icons.delete_outline, color: c.secondary),
                        onPressed: () => eq.removeDownloaded(p),
                      ),
                    ),
                ],
                if (q.length >= 2) ...[
                  header('AUTOEQ CATALOGUE · ONLINE'),
                  if (!online)
                    _hint(c, 'Turn on "Online artwork & lyrics" in Profile to search thousands more models.')
                  else if (_searching)
                    const Padding(
                      padding: EdgeInsets.all(20),
                      child: Center(child: CircularProgressIndicator(strokeWidth: 2, color: NothingColors.signalRed)),
                    )
                  else if (_searchError != null)
                    _hint(c, _searchError!)
                  else if (_online.isEmpty)
                    _hint(c, 'No measured model matches "$q". Try a shorter name, or pick a sound profile below.')
                  else
                    for (final e in _online)
                      ListTile(
                        contentPadding: const EdgeInsets.symmetric(horizontal: 20),
                        title: Text(e.name, style: TextStyle(fontFamily: NothingTypography.fontUi, color: c.display)),
                        subtitle: Text('by ${e.source}',
                            style: TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 12, color: c.secondary)),
                        trailing: _downloading == e.path
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(strokeWidth: 2, color: NothingColors.signalRed),
                              )
                            : Icon(Icons.download_outlined, color: c.secondary),
                        onTap: _downloading == null ? () => _download(e) : null,
                      ),
                ],
                header('NOT LISTED? SOUND PROFILES'),
                ...sounds.map(tile),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _hint(NothingPalette c, String text) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 8),
        child: Text(text, style: TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 12, color: c.secondary)),
      );
}
