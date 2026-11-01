import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../player/providers/player_provider.dart';
import '../../player/screens/now_playing_screen.dart';
import '../../player/widgets/track_artwork.dart';

class MiniPlayer extends StatelessWidget {
  final bool hasBottomNav;

  const MiniPlayer({
    super.key,
    this.hasBottomNav = false,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final song = context.select<PlayerProvider, dynamic>((p) => p.currentSong);
    final isPlaying = context.select<PlayerProvider, bool>((p) => p.isPlaying);
    final duration = context.select<PlayerProvider, Duration>((p) => p.duration);

    if (song == null) {
      return const SizedBox.shrink();
    }

    final player = context.read<PlayerProvider>();

    final playerWidget = GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () {
          Navigator.of(context).push(
            PageRouteBuilder(
              pageBuilder: (context, anim, secAnim) => const NowPlayingScreen(),
              transitionsBuilder: (context, anim, secAnim, child) {
                const begin = Offset(0.0, 1.0);
                const end = Offset.zero;
                const curve = Curves.easeOutCubic;
                final tween = Tween(begin: begin, end: end).chain(CurveTween(curve: curve));
                return SlideTransition(position: anim.drive(tween), child: child);
              },
            ),
          );
        },
        child: ClipRRect(
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
            child: Container(
              height: 64,
              decoration: BoxDecoration(
                color: isDark ? NothingColors.darkGlass : NothingColors.lightGlass,
                border: Border(
                  top: BorderSide(
                    color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
                    width: 1.0,
                  ),
                ),
              ),
              child: Column(
                children: [
                  // Top edge progress hairline using isolated ValueListenableBuilder
                  ValueListenableBuilder<Duration>(
                    valueListenable: player.positionListenable,
                    builder: (context, pos, _) {
                      final progress = (duration.inMilliseconds > 0)
                          ? (pos.inMilliseconds / duration.inMilliseconds).clamp(0.0, 1.0)
                          : 0.0;
                      return LinearProgressIndicator(
                        value: progress,
                        minHeight: 2.0,
                        backgroundColor: Colors.transparent,
                        valueColor: AlwaysStoppedAnimation<Color>(
                          isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                        ),
                      );
                    },
                  ),
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 14.0),
                      child: Row(
                        children: [
                          TrackArtwork(
                            songId: song.id,
                            title: song.title,
                            size: 42,
                            radius: 4,
                          ),
                          const SizedBox(width: 12),
                          // Track title and artist
                          Expanded(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    if (isPlaying) ...[
                                      Container(
                                        width: 6,
                                        height: 6,
                                        margin: const EdgeInsets.only(right: 6),
                                        decoration: const BoxDecoration(
                                          color: NothingColors.darkAccent,
                                          shape: BoxShape.circle,
                                        ),
                                      ),
                                    ],
                                    Expanded(
                                      child: Text(
                                        song.title,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: TextStyle(
                                          fontFamily: NothingTypography.fontUi,
                                          fontSize: 13,
                                          fontWeight: FontWeight.w600,
                                          color: isDark
                                              ? NothingColors.darkDisplay
                                              : NothingColors.lightDisplay,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  song.artist,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    fontFamily: NothingTypography.fontUi,
                                    fontSize: 11,
                                    color: isDark
                                        ? NothingColors.darkSecondary
                                        : NothingColors.lightSecondary,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          // Controls
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                icon: Icon(
                                  isPlaying ? Icons.pause : Icons.play_arrow,
                                  size: 24,
                                  color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                ),
                                onPressed: () => player.togglePlayPause(),
                                tooltip: isPlaying ? 'Pause' : 'Play',
                              ),
                              IconButton(
                                icon: Icon(
                                  Icons.skip_next,
                                  size: 24,
                                  color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                                ),
                                onPressed: () => player.skipToNext(),
                                tooltip: 'Next Track',
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      );

    if (hasBottomNav) {
      return playerWidget;
    }

    return SafeArea(
      top: false,
      minimum: const EdgeInsets.only(bottom: 2.0),
      child: playerWidget,
    );
  }
}
