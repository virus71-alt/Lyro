import 'dart:math' as math;

import 'package:audio_service/audio_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../core/utils/formatters.dart';
import '../providers/player_provider.dart';
import '../widgets/equalizer_sheet.dart';
import '../widgets/lyro_speaker_player.dart';
import '../widgets/progress_bar.dart';
import '../../../data/models/song_model.dart';
import '../../../core/settings/app_settings_provider.dart';
import '../../../data/services/audio_handler.dart';
import '../../../shared/widgets/dot_matrix.dart';
import '../../../shared/widgets/dot_toast.dart';
import '../widgets/lyrics_view.dart';
import '../widgets/artwork_tint.dart';
import '../widgets/queue_sheet.dart';

/// Full-screen Now Playing screen for Lyro matching the approved CMF-inspired
/// industrial design reference with 3D physical speaker, rotating artwork,
/// and responsive audio controls.
///
/// Swiping up from the track info pulls the queue sheet up with the finger;
/// the player collapses into a compact bar at the top while it is open.
class NowPlayingScreen extends StatefulWidget {
  const NowPlayingScreen({super.key});

  @override
  State<NowPlayingScreen> createState() => _NowPlayingScreenState();
}

class _NowPlayingScreenState extends State<NowPlayingScreen>
    with TickerProviderStateMixin {
  static const double _miniBarHeight = 68;
  static const double _dismissDistance = 110;

  /// 0 = queue hidden, 1 = queue fully open.
  late final AnimationController _queue = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 320),
  )..addListener(_syncQueueShown);

  /// Swipe-down-to-go-back offset in px (follows the finger with resistance).
  late final AnimationController _dismiss = AnimationController.unbounded(
    vsync: this,
  );

  double _sheetTravel = 1;

  /// Whether the queue is (partly) visible; drives only the back-button
  /// PopScope, so flipping it never rebuilds the rest of the screen.
  final ValueNotifier<bool> _queueShown = ValueNotifier(false);

  /// Bumped each time the queue opens so the list re-scrolls to the current
  /// track (listened to by the sheet itself, no parent rebuild).
  final ValueNotifier<int> _queueOpened = ValueNotifier(0);

  void _syncQueueShown() {
    final shown = _queue.value > 0;
    if (shown == _queueShown.value) return;
    _queueShown.value = shown;
    if (shown) _queueOpened.value++;
  }

  @override
  void dispose() {
    _queue.dispose();
    _dismiss.dispose();
    _queueShown.dispose();
    _queueOpened.dispose();
    super.dispose();
  }

  void _openQueue() {
    HapticFeedback.lightImpact();
    _queue.animateTo(1, curve: Curves.easeOutCubic);
  }

  void _closeQueue() => _queue.animateTo(0, curve: Curves.easeOutCubic);

  static const _sleepSteps = <int?>[null, 15, 30, 60, -1]; // -1 = end of track

  static int _sleepStep(SleepTimer? t) {
    if (t == null) return 0;
    if (t.endOfTrack) return 4;
    return switch (t.total.inMinutes) {
      15 => 1,
      30 => 2,
      60 => 3,
      _ => 1,
    };
  }

  static String _sleepLabel(SleepTimer? t) {
    if (t == null) return 'Sleep timer off';
    if (t.endOfTrack) return 'Stops at the end of this track';
    return 'Stops in ${t.remaining.inMinutes + 1} minutes';
  }

  /// Dial tap: off → 15 → 30 → 60 min → end of track → off.
  void _cycleSleep(
    BuildContext context,
    PlayerProvider player,
    SleepTimer? timer,
  ) {
    final next = _sleepSteps[(_sleepStep(timer) + 1) % _sleepSteps.length];
    switch (next) {
      case null:
        player.cancelSleepTimer();
        showDotToast(context, 'Sleep timer off');
      case -1:
        player.setSleepTimer(endOfTrack: true);
        showDotToast(context, 'Sleep · end of track');
      default:
        player.setSleepTimer(duration: Duration(minutes: next));
        showDotToast(context, 'Sleep · $next min');
    }
  }

  /// Queue sheet's own drags only move the sheet.
  void _dragQueue(double dy) {
    _queue.value = (_queue.value - dy / _sheetTravel).clamp(0.0, 1.0);
  }

  void _settleQueue(double velocity) {
    final open = velocity < -300 || (velocity <= 300 && _queue.value > 0.45);
    if (open && _queue.value < 1) HapticFeedback.lightImpact();
    open ? _queue.animateTo(1, curve: Curves.easeOutCubic) : _closeQueue();
  }

  /// Drags on the player's header / track info: up pulls the queue, down
  /// (with the queue closed) drags the screen away to go back.
  void _dragPlayer(double dy) {
    final dismissing = _dismiss.value > 0;
    if (!_queue.isAnimating && _queue.value == 0 && (dy > 0 || dismissing)) {
      _dismiss.value = math.max(0.0, _dismiss.value + dy * 0.6);
      return;
    }
    _dragQueue(dy);
  }

  void _endDragPlayer(double velocity) {
    if (_dismiss.value > 0) {
      if (_dismiss.value > _dismissDistance * 0.6 || velocity > 700) {
        HapticFeedback.lightImpact();
        Navigator.of(context).maybePop();
      } else {
        _dismiss.animateTo(
          0,
          duration: const Duration(milliseconds: 220),
          curve: Curves.easeOutCubic,
        );
      }
      return;
    }
    _settleQueue(velocity);
  }

  @override
  Widget build(BuildContext context) {
    final p = _NpPalette.of(context);
    final song = context.select<PlayerProvider, dynamic>((p) => p.currentSong);
    final duration = context.select<PlayerProvider, Duration>(
      (p) => p.duration,
    );
    final isPlaying = context.select<PlayerProvider, bool>((p) => p.isPlaying);
    final isBuffering = context.select<PlayerProvider, bool>(
      (p) => p.isBuffering,
    );
    final isShuffle = context.select<PlayerProvider, bool>((p) => p.isShuffle);
    final repeatMode = context.select<PlayerProvider, AudioServiceRepeatMode>(
      (p) => p.repeatMode,
    );

    if (song == null) {
      return Scaffold(
        backgroundColor: p.bg,
        body: Center(
          child: Text(
            'NO TRACK PLAYING',
            style: NothingTypography.monoLabel(isDark: p.dark),
          ),
        ),
      );
    }

    final isFav = context.select<PlayerProvider, bool>(
      (p) => p.isFavorite(song.id),
    );
    final player = context.read<PlayerProvider>();
    final settings = context.watch<AppSettingsProvider>();
    final colorway = settings.colorway(Theme.of(context).brightness);
    final reduceMotion = settings.motionReduced(context);

    final playerView = Stack(
      fit: StackFit.expand,
      children: [
        // 1. Subtle Background Dot Field matching reference
        Positioned.fill(
          child: CustomPaint(
            painter: NothingDotPainter(
              isDark: p.dark,
              spacing: 24.0,
              dotRadius: 0.8,
            ),
          ),
        ),

        // 2. Safe Area: header, speaker filling the free space, controls
        SafeArea(
          child: Column(
            children: [
              // Header with back chevron, centered LYRO + ● LOSSLESS, and menu
              // button; swipe down here (or on the track info) to go back
              GestureDetector(
                behavior: HitTestBehavior.translucent,
                onVerticalDragUpdate: (d) => _dragPlayer(d.delta.dy),
                onVerticalDragEnd: (d) =>
                    _endDragPlayer(d.primaryVelocity ?? 0),
                child: _buildHeader(
                  context,
                  song,
                  isPlaying,
                  player,
                  reduceMotion,
                ),
              ),

              // Centerpiece: interactive 3D CMF speaker takes every spare pixel
              Expanded(
                child: LayoutBuilder(
                  builder: (context, constraints) =>
                      ValueListenableBuilder<SleepTimer?>(
                        valueListenable: player.sleepTimer,
                        builder: (context, timer, _) => LyroSpeakerPlayer(
                          song: song,
                          isPlaying: isPlaying,
                          onPlayPause: () => player.playOrPause(),
                          sleepStep: _sleepStep(timer),
                          sleepFraction: timer?.endsAt == null
                              ? null
                              : (timer!.remaining.inMilliseconds /
                                        timer.total.inMilliseconds)
                                    .clamp(0.0, 1.0),
                          sleepLabel: _sleepLabel(timer),
                          onDialTap: () => _cycleSleep(context, player, timer),
                          shape: settings.speakerShape,
                          bodyColor: colorway.body,
                          knobColor: colorway.knob,
                          dialColor: colorway.dial,
                          logoColor: colorway.body.computeLuminance() > 0.3
                              ? const Color(0x99000000)
                              : const Color(0xB8FFFFFF),
                          reduceMotion: reduceMotion,
                          width: constraints.maxWidth,
                          height: constraints.maxHeight,
                        ),
                      ),
                ),
              ),

              // Current lyric line (tap for the full synced lyrics)
              NowPlayingLyricLine(
                song: song as Song,
                position: player.positionListenable,
                onSeek: player.seek,
              ),

              // Swipe up anywhere below the speaker to pull up the queue
              GestureDetector(
                behavior: HitTestBehavior.translucent,
                onVerticalDragUpdate: (d) => _dragPlayer(d.delta.dy),
                onVerticalDragEnd: (d) =>
                    _endDragPlayer(d.primaryVelocity ?? 0),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20.0),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // Track Information (Title, Artist, and Favorite Heart)
                      _buildTrackInfo(context, song, isFav, player),

                      const SizedBox(height: 8),

                      // Playback Progress Slider (Scrubbable timeline)
                      LyroProgressBar(
                        duration: duration,
                        positionListenable: player.positionListenable,
                        bufferedPositionListenable:
                            player.bufferedPositionListenable,
                        onSeek: (pos) => player.seek(pos),
                      ),

                      const SizedBox(height: 4),

                      // Playback Controls Row (Shuffle, Prev, large Play/Pause, Next, Repeat)
                      _buildPlaybackControls(
                        context,
                        player,
                        isPlaying,
                        isBuffering,
                        isShuffle,
                        repeatMode,
                      ),

                      const SizedBox(height: 10),

                      // Swipe-up affordance for the queue
                      _buildQueueHandle(context),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );

    return ValueListenableBuilder<bool>(
      valueListenable: _queueShown,
      builder: (context, shown, child) => PopScope(
        canPop: !shown,
        onPopInvokedWithResult: (didPop, _) {
          if (!didPop) _closeQueue();
        },
        child: child!,
      ),
      child: AnnotatedRegion<SystemUiOverlayStyle>(
        // Status-bar icons readable on this screen's own background
        value: p.dark ? SystemUiOverlayStyle.light : SystemUiOverlayStyle.dark,
        child: AnimatedBuilder(
          animation: _dismiss,
          builder: (context, child) => Transform.translate(
            offset: Offset(0, _dismiss.value),
            child: child,
          ),
          child: Scaffold(
            backgroundColor: p.bg,
            body: ArtworkTintBuilder(
              songId: song.id,
              builder: (context, tint) => LayoutBuilder(
                builder: (context, constraints) {
                  final height = constraints.maxHeight;
                  final topInset = MediaQuery.paddingOf(context).top;
                  final sheetTop = topInset + _miniBarHeight;
                  _sheetTravel = height - sheetTop;

                  // Built once per layout, not per animation frame. The sheet
                  // stays mounted (offstage while closed) so opening it never
                  // has to build the list from scratch.
                  final miniBar = RepaintBoundary(
                    child: QueueMiniPlayerBar(
                      song: song,
                      isPlaying: isPlaying,
                      onTap: _closeQueue,
                      onPrevious: player.skipToPrevious,
                      onPlayPause: player.playOrPause,
                      onNext: player.skipToNext,
                    ),
                  );
                  final sheet = RepaintBoundary(
                    child: QueueSheet(
                      background: ArtworkTint.background(
                        tint,
                        lift: 0.035,
                        dark: p.dark,
                      ),
                      onDragUpdate: _dragQueue,
                      onDragEnd: _settleQueue,
                      openSignal: _queueOpened,
                    ),
                  );
                  final backdrop = ArtworkTint.background(tint, dark: p.dark);

                  return AnimatedBuilder(
                    animation: _queue,
                    child: RepaintBoundary(child: playerView),
                    builder: (context, child) {
                      final v = _queue.value;
                      final t = Curves.easeOut.transform(v);
                      final barOpacity = ((v - 0.55) / 0.45).clamp(0.0, 1.0);
                      return Stack(
                        fit: StackFit.expand,
                        children: [
                          // Player recedes as the queue rises; once fully covered
                          // it pauses and is not drawn (its state is kept).
                          TickerMode(
                            enabled: v < 1,
                            child: Offstage(
                              // Fully covered: skip drawing it at all.
                              offstage: v >= 1,
                              child: IgnorePointer(
                                ignoring: v > 0.5,
                                child: Transform.translate(
                                  offset: Offset(0, -48 * t),
                                  child: child,
                                ),
                              ),
                            ),
                          ),
                          // Artwork-tinted backdrop fading over the player
                          if (v > 0)
                            IgnorePointer(
                              child: ColoredBox(
                                color: backdrop.withValues(alpha: t),
                              ),
                            ),
                          // Compact player bar
                          Positioned(
                            top: topInset,
                            left: 0,
                            right: 0,
                            height: _miniBarHeight,
                            child: Offstage(
                              offstage: barOpacity == 0,
                              child: IgnorePointer(
                                ignoring: barOpacity < 0.9,
                                child: Opacity(
                                  opacity: barOpacity,
                                  child: miniBar,
                                ),
                              ),
                            ),
                          ),
                          // The queue sheet itself
                          Positioned(
                            left: 0,
                            right: 0,
                            top: sheetTop + (1 - v) * _sheetTravel,
                            height: _sheetTravel,
                            child: Offstage(
                              offstage: v == 0,
                              child: TickerMode(enabled: v > 0, child: sheet),
                            ),
                          ),
                        ],
                      );
                    },
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
  }

  /// Header with minimize down-chevron, centered LYRO logo + ● LOSSLESS badge, and options menu
  Widget _buildHeader(
    BuildContext context,
    dynamic song,
    bool isPlaying,
    PlayerProvider player,
    bool reduceMotion,
  ) {
    final p = _NpPalette.of(context);
    // Quality badge label based on audio format
    final isFlac = song.isFlac as bool? ?? false;
    final qualityLabel = isFlac
        ? 'LOSSLESS'
        : song.format.toString().toUpperCase();

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          // Minimize Button (Down Arrow)
          IconButton(
            icon: Icon(Icons.keyboard_arrow_down, size: 28, color: p.display),
            onPressed: () => Navigator.of(context).pop(),
            tooltip: 'Minimize',
          ),

          // Centered Dot-Matrix Branding & Quality Indicator
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Wave while playing, Glyph light sweep while paused
              DotMatrixLogo(
                isPlaying: isPlaying,
                still: reduceMotion,
                color: p.display,
              ),
              const SizedBox(height: 1),
              ValueListenableBuilder<SleepTimer?>(
                valueListenable: player.sleepTimer,
                builder: (context, timer, formatRow) => timer == null
                    ? formatRow!
                    : Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.bedtime_outlined,
                            size: 11,
                            color: NothingColors.signalRedBright,
                          ),
                          const SizedBox(width: 5),
                          Text(
                            timer.endOfTrack
                                ? 'END OF TRACK'
                                : Formatters.formatDuration(timer.remaining),
                            style: TextStyle(
                              fontFamily: NothingTypography.fontMono,
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                              letterSpacing: 1.4,
                              color: NothingColors.signalRedBright,
                            ),
                          ),
                        ],
                      ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 5,
                      height: 5,
                      margin: const EdgeInsets.only(right: 5),
                      decoration: BoxDecoration(
                        color: NothingColors.signalRed,
                        shape: BoxShape.circle,
                      ),
                    ),
                    Text(
                      qualityLabel,
                      style: TextStyle(
                        fontFamily: NothingTypography.fontMono,
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 1.4,
                        color: p.secondary,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),

          // Options Menu Button
          IconButton(
            icon: Icon(Icons.more_horiz, size: 26, color: p.display),
            onPressed: () => _showTrackOptions(context, song),
            tooltip: 'Options',
          ),
        ],
      ),
    );
  }

  /// Track Title, Artist, and Favorite button positioned immediately below the speaker
  Widget _buildTrackInfo(
    BuildContext context,
    dynamic song,
    bool isFav,
    PlayerProvider player,
  ) {
    final p = _NpPalette.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  song.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontFamily: NothingTypography.fontUi,
                    fontSize: 24,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.4,
                    color: p.display,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  song.artist.toUpperCase(),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontFamily: NothingTypography.fontMono,
                    fontSize: 12,
                    letterSpacing: 1.5,
                    color: p.secondary,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          IconButton(
            icon: Icon(
              isFav ? Icons.favorite : Icons.favorite_border,
              size: 26,
              color: isFav ? NothingColors.signalRed : p.display,
            ),
            onPressed: () {
              HapticFeedback.lightImpact();
              player.toggleFavorite(song.id);
            },
            tooltip: isFav ? 'Remove Favorite' : 'Add to Favorites',
          ),
        ],
      ),
    );
  }

  /// Playback Controls Row matching the exact reference composition
  Widget _buildPlaybackControls(
    BuildContext context,
    PlayerProvider player,
    bool isPlaying,
    bool isBuffering,
    bool isShuffle,
    AudioServiceRepeatMode repeatMode,
  ) {
    final p = _NpPalette.of(context);
    final isRepeatOne = repeatMode == AudioServiceRepeatMode.one;
    final isRepeatAll = repeatMode == AudioServiceRepeatMode.all;

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        // Shuffle Toggle
        IconButton(
          icon: Icon(
            Icons.shuffle,
            size: 22,
            color: isShuffle ? NothingColors.signalRed : p.secondary,
          ),
          onPressed: () {
            HapticFeedback.lightImpact();
            player.toggleShuffle();
          },
          tooltip: 'Shuffle',
        ),

        // Skip Previous
        IconButton(
          icon: Icon(Icons.skip_previous, size: 32, color: p.display),
          onPressed: () {
            HapticFeedback.lightImpact();
            player.skipToPrevious();
          },
          tooltip: 'Previous',
        ),

        // Prominent Solid White Circular Play / Pause Button
        GestureDetector(
          onTap: () {
            HapticFeedback.mediumImpact();
            player.playOrPause();
          },
          child: Container(
            width: 68,
            height: 68,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: p.display,
              boxShadow: [
                BoxShadow(
                  color: p.display.withValues(alpha: 0.18),
                  blurRadius: 18,
                  spreadRadius: 2,
                ),
              ],
            ),
            child: Center(
              child: isBuffering
                  ? SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(
                        strokeWidth: 2.5,
                        valueColor: AlwaysStoppedAnimation<Color>(p.onDisplay),
                      ),
                    )
                  : Icon(
                      isPlaying ? Icons.pause : Icons.play_arrow,
                      size: 36,
                      color: p.onDisplay,
                    ),
            ),
          ),
        ),

        // Skip Next
        IconButton(
          icon: Icon(Icons.skip_next, size: 32, color: p.display),
          onPressed: () {
            HapticFeedback.lightImpact();
            player.skipToNext();
          },
          tooltip: 'Next',
        ),

        // Repeat Toggle (Cycles off -> all -> one)
        IconButton(
          icon: Icon(
            isRepeatOne ? Icons.repeat_one : Icons.repeat,
            size: 22,
            color: (isRepeatOne || isRepeatAll)
                ? NothingColors.signalRed
                : p.secondary,
          ),
          onPressed: () {
            HapticFeedback.lightImpact();
            player.toggleRepeat();
          },
          tooltip: 'Repeat',
        ),
      ],
    );
  }

  /// Small chevron + QUEUE label hinting at the swipe-up gesture (also tappable).
  Widget _buildQueueHandle(BuildContext context) {
    final p = _NpPalette.of(context);
    return GestureDetector(
      key: const Key('now_playing_queue_handle'),
      behavior: HitTestBehavior.opaque,
      onTap: _openQueue,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 4, 24, 12),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.keyboard_arrow_up_rounded,
              size: 20,
              color: p.display.withValues(alpha: 0.55),
            ),
            Text(
              'QUEUE',
              style: TextStyle(
                fontFamily: NothingTypography.fontMono,
                fontSize: 10,
                fontWeight: FontWeight.w500,
                letterSpacing: 1.6,
                color: p.display.withValues(alpha: 0.55),
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// Track context options bottom sheet
  void _showTrackOptions(BuildContext context, dynamic song) {
    final p = _NpPalette.of(context);
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => Container(
        decoration: BoxDecoration(
          color: p.surface,
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
          border: Border(top: BorderSide(color: p.line, width: 1.0)),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              song.title.toUpperCase(),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: NothingTypography.displayTitle(
                isDark: p.dark,
                fontSize: 16,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              '${song.artist} • ${song.album}',
              style: NothingTypography.monoLabel(isDark: p.dark),
            ),
            Divider(color: p.line, height: 28),
            ListTile(
              leading: Icon(Icons.info_outline, color: p.display),
              title: Text(
                'Track Details',
                style: NothingTypography.bodyMedium(isDark: p.dark),
              ),
              subtitle: Text(
                'Format: ${song.format} • Size: ${Formatters.formatFileSize(song.size ?? 0)}',
                style: NothingTypography.monoLabel(isDark: p.dark),
              ),
              dense: true,
            ),
            ListTile(
              leading: Icon(Icons.tune, color: p.display),
              title: Text(
                'Equalizer',
                style: NothingTypography.bodyMedium(isDark: p.dark),
              ),
              onTap: () {
                Navigator.of(context).pop();
                EqualizerSheet.show(context);
              },
              dense: true,
            ),
            ListTile(
              leading: Icon(Icons.playlist_play, color: p.display),
              title: Text(
                'Open Current Queue',
                style: NothingTypography.bodyMedium(isDark: p.dark),
              ),
              onTap: () {
                Navigator.of(context).pop();
                _openQueue();
              },
              dense: true,
            ),
          ],
        ),
      ),
    );
  }
}

/// Now Playing colours for the current theme (the screen used to be
/// dark-only).
class _NpPalette {
  final bool dark;
  final Color bg, surface, line, display, secondary, onDisplay;

  const _NpPalette._(
    this.dark,
    this.bg,
    this.surface,
    this.line,
    this.display,
    this.secondary,
    this.onDisplay,
  );

  static _NpPalette of(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark
      ? const _NpPalette._(
          true,
          Color(0xFF0B0B0D),
          NothingColors.darkSurface,
          NothingColors.darkLine,
          NothingColors.darkDisplay,
          NothingColors.darkSecondary,
          Colors.black,
        )
      : const _NpPalette._(
          false,
          Color(0xFFF2F2F2),
          NothingColors.lightSurface,
          NothingColors.lightLine,
          NothingColors.lightDisplay,
          NothingColors.lightSecondary,
          Colors.white,
        );
}
