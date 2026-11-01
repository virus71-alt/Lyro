import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../shared/widgets/nothing_status_badge.dart';
import '../providers/player_provider.dart';
import '../widgets/player_controls.dart';
import '../widgets/progress_bar.dart';
import '../widgets/track_artwork.dart';
import 'queue_screen.dart';

class NowPlayingScreen extends StatelessWidget {
  const NowPlayingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final song = context.select<PlayerProvider, dynamic>((p) => p.currentSong);
    final duration = context.select<PlayerProvider, Duration>((p) => p.duration);

    if (song == null) {
      return Scaffold(
        backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
        body: Center(
          child: Text(
            'NO TRACK PLAYING',
            style: NothingTypography.monoLabel(isDark: isDark),
          ),
        ),
      );
    }

    final player = context.read<PlayerProvider>();

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      body: Stack(
        fit: StackFit.expand,
        children: [
          // Dot Matrix Background
          Positioned.fill(
            child: CustomPaint(
              painter: NothingDotPainter(
                isDark: isDark,
                spacing: 24.0,
                dotRadius: 1.0,
              ),
            ),
          ),
          SafeArea(
            child: LayoutBuilder(
              builder: (context, constraints) {
                // Responsive artwork size constrained by both width and available height
                final maxByHeight = (constraints.maxHeight * 0.40).clamp(160.0, 320.0);
                final maxByWidth = (constraints.maxWidth * 0.78).clamp(160.0, 320.0);
                final artworkSize = math.min(maxByHeight, maxByWidth);

                return Column(
                  children: [
                    // Top App Bar
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 6.0),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          IconButton(
                            icon: Icon(
                              Icons.keyboard_arrow_down,
                              size: 28,
                              color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                            ),
                            onPressed: () => Navigator.of(context).pop(),
                          ),
                          Column(
                            children: [
                              Text(
                                'NOW PLAYING',
                                style: NothingTypography.monoLabel(isDark: isDark),
                              ),
                              const SizedBox(height: 2),
                              Row(
                                children: [
                                  Container(
                                    width: 5,
                                    height: 5,
                                    margin: const EdgeInsets.only(right: 5),
                                    decoration: const BoxDecoration(
                                      color: NothingColors.darkAccent,
                                      shape: BoxShape.circle,
                                    ),
                                  ),
                                  Text(
                                    song.format.toUpperCase(),
                                    style: TextStyle(
                                      fontFamily: NothingTypography.fontMono,
                                      fontSize: 10,
                                      fontWeight: FontWeight.w600,
                                      color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                          IconButton(
                            icon: Icon(
                              Icons.queue_music,
                              size: 24,
                              color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                            ),
                            onPressed: () => _openQueue(context),
                          ),
                        ],
                      ),
                    ),
                    // Scrollable & Flexible Main Content to prevent any vertical overflow
                    Expanded(
                      child: SingleChildScrollView(
                        physics: const ClampingScrollPhysics(),
                        padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 8.0),
                        child: ConstrainedBox(
                          constraints: BoxConstraints(
                            minHeight: math.max(0, constraints.maxHeight - 70),
                          ),
                          child: IntrinsicHeight(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                              children: [
                                const SizedBox(height: 4),
                                // Large Artwork
                                Center(
                                  child: TrackArtwork(
                                    songId: song.id,
                                    title: song.title,
                                    size: artworkSize,
                                    radius: NothingColors.radiusMd,
                                  ),
                                ),
                                const SizedBox(height: 16),
                                // Track Metadata with strict maxLines and ellipsis
                                Column(
                                  children: [
                                    Text(
                                      song.title,
                                      textAlign: TextAlign.center,
                                      maxLines: 2,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(
                                        fontFamily: NothingTypography.fontUi,
                                        fontSize: 20,
                                        fontWeight: FontWeight.w600,
                                        letterSpacing: -0.3,
                                        color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                      ),
                                    ),
                                    const SizedBox(height: 6),
                                    Text(
                                      song.artist.toUpperCase(),
                                      textAlign: TextAlign.center,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(
                                        fontFamily: NothingTypography.fontMono,
                                        fontSize: 12,
                                        letterSpacing: 1.2,
                                        color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                                      ),
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      song.album,
                                      textAlign: TextAlign.center,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(
                                        fontFamily: NothingTypography.fontUi,
                                        fontSize: 12,
                                        color: isDark ? NothingColors.darkMuted : NothingColors.lightMuted,
                                      ),
                                    ),
                                    if (song.isFlac) ...[
                                      const SizedBox(height: 8),
                                      const NothingStatusBadge(
                                        label: 'FLAC LOSSLESS',
                                        type: BadgeType.flacHiRes,
                                        showDot: true,
                                      ),
                                    ],
                                  ],
                                ),
                                const SizedBox(height: 16),
                                // Progress Bar (isolated listenable) & Controls
                                Column(
                                  children: [
                                    LyroProgressBar(
                                      duration: duration,
                                      positionListenable: player.positionListenable,
                                      bufferedPositionListenable: player.bufferedPositionListenable,
                                      onSeek: (pos) => player.seek(pos),
                                    ),
                                    const SizedBox(height: 16),
                                    PlayerControls(
                                      onOpenQueue: () => _openQueue(context),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 12),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  void _openQueue(BuildContext context) {
    Navigator.of(context).push(
      PageRouteBuilder(
        pageBuilder: (context, anim, secAnim) => const QueueScreen(),
        transitionsBuilder: (context, anim, secAnim, child) {
          const begin = Offset(1.0, 0.0);
          const end = Offset.zero;
          const curve = Curves.easeInOut;
          final tween = Tween(begin: begin, end: end).chain(CurveTween(curve: curve));
          return SlideTransition(position: anim.drive(tween), child: child);
        },
      ),
    );
  }
}
