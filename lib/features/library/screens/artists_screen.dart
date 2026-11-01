import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
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
    final artists = library.artists;

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
                    ? Center(
                        child: Text(
                          'NO ARTISTS FOUND',
                          style: NothingTypography.monoLabel(isDark: isDark),
                        ),
                      )
                    : ListView.builder(
                        itemCount: artists.length,
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

