import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../data/models/folder_model.dart';
import '../providers/library_provider.dart';
import '../widgets/folder_tile.dart';
import '../widgets/mini_player.dart';
import '../widgets/song_tile.dart';

class FoldersScreen extends StatelessWidget {
  const FoldersScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final library = context.watch<LibraryProvider>();
    final folders = library.folders;

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        title: Text(
          'FOLDERS',
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
                child: folders.isEmpty
                    ? Center(
                        child: Text(
                          'NO FOLDERS FOUND',
                          style: NothingTypography.monoLabel(isDark: isDark),
                        ),
                      )
                    : ListView.builder(
                        itemCount: folders.length,
                        itemBuilder: (context, index) {
                          final folder = folders[index];
                          return FolderTile(
                            folder: folder,
                            onTap: () {
                              Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (_) => _FolderSongsScreen(folder: folder),
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

class _FolderSongsScreen extends StatelessWidget {
  final MusicFolder folder;

  const _FolderSongsScreen({required this.folder});

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        title: Text(
          folder.name.toUpperCase(),
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
                child: ListView.builder(
                  itemCount: folder.songs.length,
                  itemBuilder: (context, index) {
                    final song = folder.songs[index];
                    return SongTile(
                      song: song,
                      playlist: folder.songs,
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

