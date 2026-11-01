import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollDirection;
import 'package:flutter/services.dart';

import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../data/models/song_model.dart';
import '../../../data/services/lyrics_service.dart';

/// Loads lyrics for [song] and rebuilds when they arrive (or not).
class _LyricsLoader extends StatefulWidget {
  final Song song;
  final Widget Function(BuildContext context, Lyrics? lyrics) builder;
  const _LyricsLoader({required this.song, required this.builder});

  @override
  State<_LyricsLoader> createState() => _LyricsLoaderState();
}

class _LyricsLoaderState extends State<_LyricsLoader> {
  Lyrics? _lyrics;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(covariant _LyricsLoader old) {
    super.didUpdateWidget(old);
    if (old.song.path != widget.song.path) {
      _lyrics = null;
      _load();
    }
  }

  Future<void> _load() async {
    final path = widget.song.path;
    final lyrics = await LyricsService.forSong(widget.song);
    if (mounted && path == widget.song.path) setState(() => _lyrics = lyrics);
  }

  @override
  Widget build(BuildContext context) => widget.builder(context, _lyrics);
}

/// Dot-matrix style current-line display under the speaker. Tap to open the
/// full synced lyrics. Renders nothing when the song has no lyrics.
class NowPlayingLyricLine extends StatelessWidget {
  final Song song;
  final ValueListenable<Duration> position;
  final ValueChanged<Duration> onSeek;

  const NowPlayingLyricLine({
    super.key,
    required this.song,
    required this.position,
    required this.onSeek,
  });

  /// Doto (dot-matrix) covers Latin text; other scripts use Geist.
  static bool _latin(String s) => !RegExp(r'[^\x00-\x7F’‘“”…]').hasMatch(s);

  @override
  Widget build(BuildContext context) {
    final c = _LyricColors.of(context);
    return _LyricsLoader(
      song: song,
      builder: (context, lyrics) {
        if (lyrics == null) return const SizedBox(height: 4);
        return GestureDetector(
          key: const Key('lyrics_line'),
          behavior: HitTestBehavior.opaque,
          onTap: () => LyricsSheet.show(context, song: song, lyrics: lyrics, position: position, onSeek: onSeek),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(24, 2, 24, 6),
            child: SizedBox(
              height: 26,
              child: ValueListenableBuilder<Duration>(
                valueListenable: position,
                builder: (context, pos, _) {
                  final i = lyrics.indexAt(pos);
                  final text = !lyrics.synced
                      ? 'LYRICS'
                      : (i < 0 ? '• • •' : lyrics.lines[i].text);
                  final shown = text.trim().isEmpty ? '♪' : text;
                  return AnimatedSwitcher(
                    duration: const Duration(milliseconds: 250),
                    transitionBuilder: (child, a) => FadeTransition(
                      opacity: a,
                      child: SlideTransition(
                        position: Tween(begin: const Offset(0, 0.4), end: Offset.zero).animate(a),
                        child: child,
                      ),
                    ),
                    child: Row(
                      key: ValueKey(shown),
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Flexible(
                          child: Text(
                            _latin(shown) ? shown.toUpperCase() : shown,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: _latin(shown)
                                ? NothingTypography.dottedBold(
                                    isDark: c.dark,
                                    fontSize: 15,
                                    letterSpacing: 1.2,
                                    color: c.primary,
                                  )
                                : TextStyle(
                                    fontFamily: NothingTypography.fontUi,
                                    fontSize: 15,
                                    fontWeight: FontWeight.w600,
                                    color: c.primary,
                                  ),
                          ),
                        ),
                        const SizedBox(width: 6),
                        Icon(Icons.lyrics_outlined, size: 14, color: c.muted),
                      ],
                    ),
                  );
                },
              ),
            ),
          ),
        );
      },
    );
  }
}

/// Full lyrics: synced lines highlight and scroll with playback; tap a line
/// to jump there.
class LyricsSheet extends StatefulWidget {
  final Song song;
  final Lyrics lyrics;
  final ValueListenable<Duration> position;
  final ValueChanged<Duration> onSeek;

  const LyricsSheet({
    super.key,
    required this.song,
    required this.lyrics,
    required this.position,
    required this.onSeek,
  });

  static Future<void> show(
    BuildContext context, {
    required Song song,
    required Lyrics lyrics,
    required ValueListenable<Duration> position,
    required ValueChanged<Duration> onSeek,
  }) {
    HapticFeedback.lightImpact();
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => LyricsSheet(song: song, lyrics: lyrics, position: position, onSeek: onSeek),
    );
  }

  @override
  State<LyricsSheet> createState() => _LyricsSheetState();
}

class _LyricsSheetState extends State<LyricsSheet> {
  static const double _lineHeight = 52;
  final ScrollController _scroll = ScrollController();
  int _current = -2;
  bool _userScrolling = false;

  @override
  void initState() {
    super.initState();
    widget.position.addListener(_follow);
    WidgetsBinding.instance.addPostFrameCallback((_) => _follow(jump: true));
  }

  @override
  void dispose() {
    widget.position.removeListener(_follow);
    _scroll.dispose();
    super.dispose();
  }

  void _follow({bool jump = false}) {
    final i = widget.lyrics.indexAt(widget.position.value);
    if (i == _current && !jump) return;
    setState(() => _current = i);
    if (_userScrolling || !_scroll.hasClients || i < 0) return;
    final target = (i * _lineHeight - _scroll.position.viewportDimension * 0.35)
        .clamp(0.0, _scroll.position.maxScrollExtent);
    if (jump) {
      _scroll.jumpTo(target);
    } else {
      _scroll.animateTo(target, duration: const Duration(milliseconds: 400), curve: Curves.easeOutCubic);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = _LyricColors.of(context);
    final lyrics = widget.lyrics;
    return DraggableScrollableSheet(
      initialChildSize: 0.86,
      maxChildSize: 0.95,
      minChildSize: 0.4,
      expand: false,
      builder: (context, sheetScroll) => Container(
        decoration: BoxDecoration(
          color: Color(0xFF0B0B0D),
          borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
          border: Border(top: BorderSide(color: c.line)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header (also drags the sheet)
            SingleChildScrollView(
              controller: sheetScroll,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(24, 10, 24, 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Center(
                      child: Container(
                        width: 36,
                        height: 4,
                        decoration: BoxDecoration(
                          color: c.line2,
                          borderRadius: BorderRadius.circular(2),
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text('LYRICS', style: NothingTypography.monoLabel(isDark: c.dark)),
                    const SizedBox(height: 4),
                    Text(
                      '${widget.song.title} · ${widget.song.artist}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontFamily: NothingTypography.fontUi,
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: c.display,
                      ),
                    ),
                    if (lyrics.source == 'lrclib')
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Text(
                          'VIA LRCLIB.NET',
                          style: NothingTypography.monoLabel(isDark: c.dark, color: c.muted),
                        ),
                      ),
                  ],
                ),
              ),
            ),
            Expanded(
              child: NotificationListener<UserScrollNotification>(
                onNotification: (n) {
                  _userScrolling = n.direction != ScrollDirection.idle;
                  return false;
                },
                child: ListView.builder(
                  controller: _scroll,
                  padding: const EdgeInsets.fromLTRB(24, 8, 24, 160),
                  itemCount: lyrics.lines.length,
                  itemExtent: lyrics.synced ? _lineHeight : null,
                  itemBuilder: (context, i) {
                    final line = lyrics.lines[i];
                    final active = lyrics.synced && i == _current;
                    final past = lyrics.synced && i < _current;
                    final text = Text(
                      line.text.isEmpty ? '♪' : line.text,
                      maxLines: lyrics.synced ? 2 : null,
                      overflow: lyrics.synced ? TextOverflow.ellipsis : null,
                      style: TextStyle(
                        fontFamily: NothingTypography.fontUi,
                        fontSize: active ? 22 : 19,
                        fontWeight: active ? FontWeight.w700 : FontWeight.w500,
                        height: 1.2,
                        color: active
                            ? c.display
                            : (past ? c.muted : c.secondary),
                      ),
                    );
                    if (!lyrics.synced) {
                      return Padding(padding: const EdgeInsets.symmetric(vertical: 4), child: text);
                    }
                    return GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onTap: () {
                        HapticFeedback.selectionClick();
                        _userScrolling = false;
                        widget.onSeek(line.time);
                      },
                      child: Row(
                        children: [
                          AnimatedContainer(
                            duration: const Duration(milliseconds: 200),
                            width: active ? 6 : 0,
                            height: 6,
                            margin: EdgeInsets.only(right: active ? 10 : 0),
                            decoration: const BoxDecoration(
                              color: NothingColors.signalRed,
                              shape: BoxShape.circle,
                            ),
                          ),
                          Expanded(child: text),
                        ],
                      ),
                    );
                  },
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}


class _LyricColors {
  final bool dark;
  final Color bg, primary, display, secondary, muted, line, line2;
  const _LyricColors(this.dark, this.bg, this.primary, this.display, this.secondary,
      this.muted, this.line, this.line2);

  static _LyricColors of(BuildContext context) => Theme.of(context).brightness == Brightness.dark
      ? const _LyricColors(true, Color(0xFF0B0B0D), NothingColors.darkPrimary,
          NothingColors.darkDisplay, NothingColors.darkSecondary, NothingColors.darkMuted,
          NothingColors.darkLine, NothingColors.darkLine2)
      : const _LyricColors(false, Color(0xFFF2F2F2), NothingColors.lightPrimary,
          NothingColors.lightDisplay, NothingColors.lightSecondary, NothingColors.lightMuted,
          NothingColors.lightLine, NothingColors.lightLine2);
}
