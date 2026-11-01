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
import 'queue_screen.dart';

/// Full-screen Now Playing screen for Lyro matching the approved CMF-inspired
/// industrial design reference with 3D physical speaker, rotating artwork,
/// and responsive audio controls.
class NowPlayingScreen extends StatelessWidget {
  const NowPlayingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final song = context.select<PlayerProvider, dynamic>((p) => p.currentSong);
    final duration = context.select<PlayerProvider, Duration>((p) => p.duration);
    final isPlaying = context.select<PlayerProvider, bool>((p) => p.isPlaying);
    final isBuffering = context.select<PlayerProvider, bool>((p) => p.isBuffering);
    final isShuffle = context.select<PlayerProvider, bool>((p) => p.isShuffle);
    final repeatMode = context.select<PlayerProvider, AudioServiceRepeatMode>((p) => p.repeatMode);

    if (song == null) {
      return Scaffold(
        backgroundColor: const Color(0xFF0B0B0D),
        body: Center(
          child: Text(
            'NO TRACK PLAYING',
            style: NothingTypography.monoLabel(isDark: true),
          ),
        ),
      );
    }

    final isFav = context.select<PlayerProvider, bool>((p) => p.isFavorite(song.id));
    final player = context.read<PlayerProvider>();

    return Scaffold(
      backgroundColor: const Color(0xFF0B0B0D),
      body: Stack(
        fit: StackFit.expand,
        children: [
          // 1. Subtle Background Dot Field matching reference
          Positioned.fill(
            child: CustomPaint(
              painter: NothingDotPainter(
                isDark: true,
                spacing: 24.0,
                dotRadius: 0.8,
              ),
            ),
          ),

          // 2. Safe Area: header, speaker filling the free space, controls
          SafeArea(
            child: Column(
              children: [
                // Header with back chevron, centered LYRO + ● LOSSLESS, and menu button
                _buildHeader(context, song),

                // Centerpiece: interactive 3D CMF speaker takes every spare pixel
                Expanded(
                  child: LayoutBuilder(
                    builder: (context, constraints) => LyroSpeakerPlayer(
                      song: song,
                      isPlaying: isPlaying,
                      isShuffle: isShuffle,
                      onPlayPause: () => player.playOrPause(),
                      onToggleShuffle: () => player.toggleShuffle(),
                      width: constraints.maxWidth,
                      height: constraints.maxHeight,
                    ),
                  ),
                ),

                Padding(
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
                        bufferedPositionListenable: player.bufferedPositionListenable,
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

                      const SizedBox(height: 14),

                      // Bottom Navigation Capsule Panel (Queue, Equalizer, Library)
                      _buildBottomNavigationPanel(context),

                      const SizedBox(height: 16),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// Header with minimize down-chevron, centered LYRO logo + ● LOSSLESS badge, and options menu
  Widget _buildHeader(BuildContext context, dynamic song) {
    // Quality badge label based on audio format
    final isFlac = song.isFlac as bool? ?? false;
    final qualityLabel = isFlac ? 'LOSSLESS' : song.format.toString().toUpperCase();

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          // Minimize Button (Down Arrow)
          IconButton(
            icon: const Icon(
              Icons.keyboard_arrow_down,
              size: 28,
              color: NothingColors.darkDisplay,
            ),
            onPressed: () => Navigator.of(context).pop(),
            tooltip: 'Minimize',
          ),

          // Centered Dot-Matrix Branding & Quality Indicator
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'LYRO',
                style: NothingTypography.dottedBold(
                  isDark: true,
                  fontSize: 14,
                  letterSpacing: 2.2,
                  color: NothingColors.darkDisplay,
                ),
              ),
              const SizedBox(height: 3),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 5,
                    height: 5,
                    margin: const EdgeInsets.only(right: 5),
                    decoration: const BoxDecoration(
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
                      color: NothingColors.darkSecondary,
                    ),
                  ),
                ],
              ),
            ],
          ),

          // Options Menu Button
          IconButton(
            icon: const Icon(
              Icons.more_horiz,
              size: 26,
              color: NothingColors.darkDisplay,
            ),
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
                  style: const TextStyle(
                    fontFamily: NothingTypography.fontUi,
                    fontSize: 24,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.4,
                    color: Colors.white,
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
                    color: NothingColors.darkSecondary,
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
              color: isFav ? NothingColors.signalRed : NothingColors.darkDisplay,
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
            color: isShuffle ? NothingColors.signalRed : NothingColors.darkSecondary,
          ),
          onPressed: () {
            HapticFeedback.lightImpact();
            player.toggleShuffle();
          },
          tooltip: 'Shuffle',
        ),

        // Skip Previous
        IconButton(
          icon: const Icon(
            Icons.skip_previous,
            size: 32,
            color: NothingColors.darkDisplay,
          ),
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
              color: Colors.white,
              boxShadow: [
                BoxShadow(
                  color: Colors.white.withValues(alpha: 0.18),
                  blurRadius: 18,
                  spreadRadius: 2,
                ),
              ],
            ),
            child: Center(
              child: isBuffering
                  ? const SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(
                        strokeWidth: 2.5,
                        valueColor: AlwaysStoppedAnimation<Color>(Colors.black),
                      ),
                    )
                  : Icon(
                      isPlaying ? Icons.pause : Icons.play_arrow,
                      size: 36,
                      color: Colors.black,
                    ),
            ),
          ),
        ),

        // Skip Next
        IconButton(
          icon: const Icon(
            Icons.skip_next,
            size: 32,
            color: NothingColors.darkDisplay,
          ),
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
                : NothingColors.darkSecondary,
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

  /// Rounded Bottom Navigation Capsule Panel (Queue, Equalizer, Library)
  /// Responsive layout guarantees zero overflow on any screen width
  Widget _buildBottomNavigationPanel(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        return Container(
          constraints: BoxConstraints(
            maxWidth: math.min(constraints.maxWidth, 340.0),
          ),
          decoration: BoxDecoration(
            color: const Color(0xFF16171A),
            borderRadius: BorderRadius.circular(NothingColors.radiusPill),
            border: Border.all(
              color: const Color(0xFF282A30),
              width: 1.0,
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.5),
                blurRadius: 14,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 4.0),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              Flexible(
                child: _navCapsuleItem(
                  icon: Icons.queue_music,
                  label: 'QUEUE',
                  onTap: () {
                    Navigator.of(context).push(
                      MaterialPageRoute(builder: (_) => const QueueScreen()),
                    );
                  },
                ),
              ),
              Container(
                width: 1.0,
                height: 16.0,
                color: const Color(0xFF2E313A),
                margin: const EdgeInsets.symmetric(horizontal: 4.0),
              ),
              Flexible(
                child: _navCapsuleItem(
                  icon: Icons.tune,
                  label: 'EQUALIZER',
                  onTap: () => EqualizerSheet.show(context),
                ),
              ),
              Container(
                width: 1.0,
                height: 16.0,
                color: const Color(0xFF2E313A),
                margin: const EdgeInsets.symmetric(horizontal: 4.0),
              ),
              Flexible(
                child: _navCapsuleItem(
                  icon: Icons.library_music,
                  label: 'LIBRARY',
                  onTap: () => Navigator.of(context).pop(),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _navCapsuleItem({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: () {
        HapticFeedback.lightImpact();
        onTap();
      },
      borderRadius: BorderRadius.circular(NothingColors.radiusPill),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 6.0, vertical: 6.0),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              size: 15,
              color: Colors.white,
            ),
            const SizedBox(width: 5),
            Flexible(
              child: Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontFamily: NothingTypography.fontMono,
                  fontSize: 10,
                  fontWeight: FontWeight.w600,
                  letterSpacing: 0.8,
                  color: Colors.white,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// Track context options bottom sheet
  void _showTrackOptions(BuildContext context, dynamic song) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => Container(
        decoration: const BoxDecoration(
          color: NothingColors.darkSurface,
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
          border: Border(
            top: BorderSide(color: NothingColors.darkLine, width: 1.0),
          ),
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
              style: NothingTypography.displayTitle(isDark: true, fontSize: 16),
            ),
            const SizedBox(height: 4),
            Text(
              '${song.artist} • ${song.album}',
              style: NothingTypography.monoLabel(isDark: true),
            ),
            const Divider(color: NothingColors.darkLine, height: 28),
            ListTile(
              leading: const Icon(Icons.info_outline, color: NothingColors.darkDisplay),
              title: Text('Track Details', style: NothingTypography.bodyMedium(isDark: true)),
              subtitle: Text(
                'Format: ${song.format} • Size: ${Formatters.formatFileSize(song.size ?? 0)}',
                style: NothingTypography.monoLabel(isDark: true),
              ),
              dense: true,
            ),
            ListTile(
              leading: const Icon(Icons.tune, color: NothingColors.darkDisplay),
              title: Text('Equalizer', style: NothingTypography.bodyMedium(isDark: true)),
              onTap: () {
                Navigator.of(context).pop();
                EqualizerSheet.show(context);
              },
              dense: true,
            ),
            ListTile(
              leading: const Icon(Icons.playlist_play, color: NothingColors.darkDisplay),
              title: Text('Open Current Queue', style: NothingTypography.bodyMedium(isDark: true)),
              onTap: () {
                Navigator.of(context).pop();
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const QueueScreen()),
                );
              },
              dense: true,
            ),
          ],
        ),
      ),
    );
  }
}
