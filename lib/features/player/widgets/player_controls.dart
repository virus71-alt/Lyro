import 'package:audio_service/audio_service.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../providers/player_provider.dart';

class PlayerControls extends StatelessWidget {
  final VoidCallback onOpenQueue;

  const PlayerControls({
    super.key,
    required this.onOpenQueue,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final player = context.watch<PlayerProvider>();
    final isPlaying = player.isPlaying;
    final isShuffle = player.isShuffle;
    final repeatMode = player.repeatMode;
    final currentSong = player.currentSong;
    final isFav = currentSong != null && player.isFavorite(currentSong.id);

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        // Secondary Action Row: Shuffle, Favorite, Repeat, Queue
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24.0),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              // Shuffle
              IconButton(
                icon: Icon(
                  Icons.shuffle,
                  size: 22,
                  color: isShuffle
                      ? (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay)
                      : (isDark ? NothingColors.darkMuted : NothingColors.lightMuted),
                ),
                onPressed: () => player.toggleShuffle(),
                tooltip: 'Shuffle',
              ),
              // Favorite
              IconButton(
                icon: Icon(
                  isFav ? Icons.favorite : Icons.favorite_border,
                  size: 22,
                  color: isFav
                      ? NothingColors.darkAccentText
                      : (isDark ? NothingColors.darkMuted : NothingColors.lightMuted),
                ),
                onPressed: currentSong != null
                    ? () => player.toggleFavorite(currentSong.id)
                    : null,
                tooltip: 'Favorite',
              ),
              // Repeat
              IconButton(
                icon: Icon(
                  repeatMode == AudioServiceRepeatMode.one
                      ? Icons.repeat_one
                      : Icons.repeat,
                  size: 22,
                  color: repeatMode != AudioServiceRepeatMode.none
                      ? (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay)
                      : (isDark ? NothingColors.darkMuted : NothingColors.lightMuted),
                ),
                onPressed: () => player.toggleRepeat(),
                tooltip: 'Repeat',
              ),
              // Queue
              IconButton(
                icon: Icon(
                  Icons.queue_music,
                  size: 22,
                  color: isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary,
                ),
                onPressed: onOpenQueue,
                tooltip: 'Queue',
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        // Primary Playback Controls: Previous, Play/Pause, Next
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Previous
            IconButton(
              icon: Icon(
                Icons.skip_previous,
                size: 34,
                color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
              ),
              onPressed: () => player.skipToPrevious(),
            ),
            const SizedBox(width: 32),
            // Play / Pause (Large Inverted Monochrome Circle)
            GestureDetector(
              onTap: () => player.togglePlayPause(),
              child: Container(
                width: 68,
                height: 68,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                ),
                child: Center(
                  child: Icon(
                    isPlaying ? Icons.pause : Icons.play_arrow,
                    size: 36,
                    color: isDark ? NothingColors.darkBg : NothingColors.lightBg,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 32),
            // Next
            IconButton(
              icon: Icon(
                Icons.skip_next,
                size: 34,
                color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
              ),
              onPressed: () => player.skipToNext(),
            ),
          ],
        ),
      ],
    );
  }
}

