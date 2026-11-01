import 'package:flutter/material.dart';
import '../../../shared/widgets/dot_matrix.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../shared/widgets/alphabet_scroll_list.dart';
import '../providers/library_provider.dart';
import '../widgets/artist_tile.dart';
import '../widgets/mini_player.dart';
import 'artist_detail_screen.dart';

class ArtistsScreen extends StatelessWidget {
  const ArtistsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final library = context.watch<LibraryProvider>();
    // Alphabetical so the A–Z rail jumps to the right place
    final artists = [...library.artists]
      ..sort((a, b) => a.artist.toLowerCase().compareTo(b.artist.toLowerCase()));

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        title: Text(
          'ARTISTS',
          style: NothingTypography.displayTitle(isDark: isDark, fontSize: 18),
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
                child: artists.isEmpty
                    ? DotEmptyState(artId: DotEmptyState.ghost, title: 'No artists found', hint: null)
                    : AlphabetScrollList(
                        items: artists,
                        letterOf: (a) => AlphabetScrollList.initialOf(a.artist),
                        itemBuilder: (context, index) {
                          final artist = artists[index];
                          return ArtistTile(
                            artist: artist,
                            onTap: () {
                              Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (_) => ArtistDetailScreen(artist: artist),
                                ),
                              );
                            },
                          );
                        },
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

