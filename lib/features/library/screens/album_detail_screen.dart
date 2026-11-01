import 'package:flutter/material.dart';
import 'package:on_audio_query/on_audio_query.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../core/utils/formatters.dart';
import '../../../data/models/album_model.dart';
import '../../../shared/widgets/nothing_button.dart';
import '../../player/providers/player_provider.dart';
import '../../player/widgets/track_artwork.dart';
import '../providers/library_provider.dart';
import '../widgets/mini_player.dart';
import '../widgets/song_tile.dart';

class AlbumDetailScreen extends StatelessWidget {
  final Album album;

  const AlbumDetailScreen({super.key, required this.album});

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final library = context.watch<LibraryProvider>();
    final albumSongs = library.getSongsForAlbum(album.id);

    final totalDurationMs = albumSongs.fold<int>(0, (sum, s) => sum + s.duration);

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        title: Text(
          album.album.toUpperCase(),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: NothingTypography.displayTitle(isDark: isDark, fontSize: 16),
        ),
      ),
      body: Stack(
        fit: StackFit.expand,
        children: [
          Positioned.fill(
            child: CustomPaint(
              painter: NothingDotPainter(
                isDark: isDark,
                spacing: 24.0,
                dotRadius: 1.0,
              ),
            ),
          ),
          Column(
            children: [
              Expanded(
                child: CustomScrollView(
                  slivers: [
                    // Album Header Info
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.all(20.0),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            TrackArtwork(
                              songId: album.songId ?? (albumSongs.isNotEmpty ? albumSongs.first.id : album.id),
                              title: album.album,
                              type: (album.songId != null || albumSongs.isNotEmpty)
                                  ? ArtworkType.AUDIO
                                  : ArtworkType.ALBUM,
                              size: 110,
                              radius: NothingColors.radiusSm,
                            ),
                            const SizedBox(width: 16),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    album.album,
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(
                                      fontFamily: NothingTypography.fontUi,
                                      fontSize: 18,
                                      fontWeight: FontWeight.w600,
                                      color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    album.artist,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(
                                      fontFamily: NothingTypography.fontUi,
                                      fontSize: 13,
                                      color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                                    ),
                                  ),
                                  const SizedBox(height: 6),
                                  Text(
                                    '${Formatters.formatTrackCount(albumSongs.length)} • ${Formatters.formatMs(totalDurationMs)}',
                                    style: TextStyle(
                                      fontFamily: NothingTypography.fontMono,
                                      fontSize: 10,
                                      color: isDark ? NothingColors.darkMuted : NothingColors.lightMuted,
                                    ),
                                  ),
                                  const SizedBox(height: 12),
                                  Row(
                                    children: [
                                      NothingButton(
                                        text: 'PLAY',
                                        variant: NothingButtonVariant.primary,
                                        icon: const Icon(Icons.play_arrow, size: 14),
                                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                                        onPressed: albumSongs.isNotEmpty
                                            ? () => context.read<PlayerProvider>().playSong(albumSongs.first, albumSongs, source: 'Album · ${album.album}')
                                            : null,
                                      ),
                                      const SizedBox(width: 8),
                                      NothingButton(
                                        text: 'SHUFFLE',
                                        variant: NothingButtonVariant.secondary,
                                        icon: const Icon(Icons.shuffle, size: 14),
                                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                                        onPressed: albumSongs.isNotEmpty
                                            ? () {
                                                final shuffled = List.of(albumSongs)..shuffle();
                                                context.read<PlayerProvider>().playSong(shuffled.first, shuffled, source: 'Album · ${album.album}');
                                              }
                                            : null,
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SliverToBoxAdapter(
                      child: Divider(height: 1),
                    ),
                    // Track list
                    SliverList(
                      delegate: SliverChildBuilderDelegate(
                        (context, index) {
                          final song = albumSongs[index];
                          return SongTile(
                            song: song,
                            playlist: albumSongs,
                            source: 'Album · ${album.album}',
                            showArtwork: false, // Track in album doesn't need redundant artwork
                          );
                        },
                        childCount: albumSongs.length,
                      ),
                    ),
                  ],
                ),
              ),
              const MiniPlayer(),
            ],
          ),
        ],
      ),
    );
  }
}
