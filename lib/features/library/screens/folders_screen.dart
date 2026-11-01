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
  final bool isTab;

  const FoldersScreen({
    super.key,
    this.isTab = false,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final library = context.watch<LibraryProvider>();
    final folders = library.folders;

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        automaticallyImplyLeading: !isTab,
        title: isTab
            ? Row(
                children: [
                  Text(
                    'FOLDERS',
                    style: NothingTypography.displayHero(isDark: isDark, fontSize: 36),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    width: 8,
                    height: 8,
                    decoration: const BoxDecoration(
                      color: NothingColors.darkAccent,
                      shape: BoxShape.circle,
                    ),
                  ),
                ],
              )
            : Text(
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
                        padding: EdgeInsets.fromLTRB(20.0, 0, 20.0, isTab ? 140.0 : 16.0),
                        itemCount: folders.length + 1,
                        itemBuilder: (context, i) {
                          if (i == 0) {
                            final tracks = folders.fold<int>(0, (n, f) => n + f.songCount);
                            return Padding(
                              padding: const EdgeInsets.only(top: 4, bottom: 6),
                              child: Text(
                                '${folders.length} FOLDERS  ·  $tracks TRACKS',
                                style: NothingTypography.monoLabel(isDark: isDark),
                              ),
                            );
                          }
                          final index = i - 1;
                          final folder = folders[index];
                          return FolderTile(
                            folder: folder,
                            index: index,
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
                      source: 'Folder · ${folder.name}',
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

