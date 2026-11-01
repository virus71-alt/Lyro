import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../core/utils/formatters.dart';
import '../../../data/models/artist_model.dart';
import '../../../shared/widgets/nothing_button.dart';
import '../../player/providers/player_provider.dart';
import '../providers/library_provider.dart';
import '../widgets/mini_player.dart';
import '../widgets/song_tile.dart';

class ArtistDetailScreen extends StatelessWidget {
  final Artist artist;

  const ArtistDetailScreen({super.key, required this.artist});

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final library = context.watch<LibraryProvider>();
    final artistSongs = library.getSongsForArtist(artist.artist);

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        title: Text(
          artist.artist.toUpperCase(),
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
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.all(20.0),
                        child: Row(
                          children: [
                            Container(
                              width: 80,
                              height: 80,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: isDark ? NothingColors.darkRaised : NothingColors.lightRaised,
                                border: Border.all(
                                  color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
                                  width: 1.0,
                                ),
                              ),
                              child: Center(
                                child: Text(
                                  artist.artist.isNotEmpty ? artist.artist[0].toUpperCase() : 'A',
                                  style: NothingTypography.dottedBold(
                                    isDark: isDark,
                                    fontSize: 32,
                                    color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                  ),
                                ),
                              ),
                            ),
                            const SizedBox(width: 18),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    artist.artist,
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
                                    '${artist.numberOfAlbums} ALBUMS • ${Formatters.formatTrackCount(artistSongs.length)}',
                                    style: TextStyle(
                                      fontFamily: NothingTypography.fontMono,
                                      fontSize: 10,
                                      color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
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
                                        onPressed: artistSongs.isNotEmpty
                                            ? () => context.read<PlayerProvider>().playSong(artistSongs.first, artistSongs)
                                            : null,
                                      ),
                                      const SizedBox(width: 8),
                                      NothingButton(
                                        text: 'SHUFFLE',
                                        variant: NothingButtonVariant.secondary,
                                        icon: const Icon(Icons.shuffle, size: 14),
                                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                                        onPressed: artistSongs.isNotEmpty
                                            ? () {
                                                final shuffled = List.of(artistSongs)..shuffle();
                                                context.read<PlayerProvider>().playSong(shuffled.first, shuffled);
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
                    const SliverToBoxAdapter(child: Divider(height: 1)),
                    SliverList(
                      delegate: SliverChildBuilderDelegate(
                        (context, index) {
                          final song = artistSongs[index];
                          return SongTile(
                            song: song,
                            playlist: artistSongs,
                          );
                        },
                        childCount: artistSongs.length,
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
