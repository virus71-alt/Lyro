import 'package:flutter/material.dart';
import '../../../shared/widgets/dot_matrix.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../core/utils/formatters.dart';
import '../../../data/models/folder_model.dart';
import '../../player/providers/player_provider.dart';
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
    // Hearts are toggled on PlayerProvider; this keeps the row in step.
    context.select<PlayerProvider, int>((p) => p.favoriteCount);
    final favorites = _favoritesFolder(library);
    final extraRows = favorites.songs.isEmpty ? 0 : 1;

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
                    ? DotEmptyState(artId: DotEmptyState.vinyl, title: 'No folders found', hint: 'Music in your storage will appear here after a scan.')
                    : ListView.builder(
                        padding: EdgeInsets.fromLTRB(20.0, 0, 20.0, isTab ? 140.0 : 16.0),
                        itemCount: folders.length + 1 + extraRows,
                        itemBuilder: (context, i) {
                          if (i == 0) {
                            final tracks = folders.fold<int>(0, (n, f) => n + f.songCount);
                            final playTime = folders.fold<int>(0, (ms, f) => ms + f.totalDuration);
                            return Padding(
                              padding: const EdgeInsets.only(top: 4, bottom: 6),
                              child: Text(
                                '${folders.length} FOLDERS  ·  $tracks TRACKS  ·  ${Formatters.formatPlayTime(playTime)}',
                                style: NothingTypography.monoLabel(isDark: isDark),
                              ),
                            );
                          }
                          if (extraRows == 1 && i == 1) {
                            return FolderTile(
                              key: const Key('favorites_folder'),
                              folder: favorites,
                              indexLabel: '♥',
                              cover: const FavoritesCover(),
                              subtitle: 'HEARTED SONGS',
                              onTap: () {
                                Navigator.of(context).push(
                                  MaterialPageRoute(builder: (_) => const _FavoritesScreen()),
                                );
                              },
                            );
                          }
                          final index = i - 1 - extraRows;
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

MusicFolder _favoritesFolder(LibraryProvider library) =>
    MusicFolder(path: '', name: 'Favorites', songs: library.favoriteSongs);

/// The hearted songs; unhearting one here takes it off the list.
class _FavoritesScreen extends StatelessWidget {
  const _FavoritesScreen();

  @override
  Widget build(BuildContext context) {
    context.select<PlayerProvider, int>((p) => p.favoriteCount);
    return _FolderSongsScreen(folder: _favoritesFolder(context.watch<LibraryProvider>()));
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
                child: folder.songs.isEmpty
                    ? const DotEmptyState(
                        artId: DotEmptyState.heart,
                        title: 'No favorites yet',
                        hint: 'Tap the heart on any song to keep it here.',
                      )
                    : ListView.builder(
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

