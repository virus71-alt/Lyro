import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../providers/library_provider.dart';
import '../widgets/album_card.dart';
import '../widgets/mini_player.dart';
import 'album_detail_screen.dart';

class AlbumsScreen extends StatelessWidget {
  const AlbumsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final library = context.watch<LibraryProvider>();
    final albums = library.albums;

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        title: Text(
          'ALBUMS',
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
                child: albums.isEmpty
                    ? Center(
                        child: Text(
                          'NO ALBUMS FOUND',
                          style: NothingTypography.monoLabel(isDark: isDark),
                        ),
                      )
                    : GridView.builder(
                        padding: const EdgeInsets.all(16.0),
                        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          crossAxisSpacing: 14.0,
                          mainAxisSpacing: 14.0,
                          childAspectRatio: 0.78,
                        ),
                        itemCount: albums.length,
                        itemBuilder: (context, index) {
                          final album = albums[index];
                          return AlbumCard(
                            album: album,
                            onTap: () {
                              Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (_) => AlbumDetailScreen(album: album),
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

