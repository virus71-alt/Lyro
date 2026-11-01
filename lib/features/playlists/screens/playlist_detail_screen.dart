import 'package:flutter/material.dart';
import '../../../core/theme/nothing_palette.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../core/utils/formatters.dart';
import '../../../shared/widgets/collection_header.dart';
import '../../../shared/widgets/dot_matrix.dart';
import '../../../shared/widgets/dot_toast.dart';
import '../../library/providers/library_provider.dart';
import '../../library/widgets/mini_player.dart';
import '../../library/widgets/song_tile.dart';
import '../../player/providers/player_provider.dart';
import '../providers/playlist_provider.dart';
import '../widgets/add_to_playlist_sheet.dart';

class PlaylistDetailScreen extends StatelessWidget {
  final String playlistId;
  const PlaylistDetailScreen({super.key, required this.playlistId});

  @override
  Widget build(BuildContext context) {
    final c = NothingPalette.of(context);
    final isDark = context.isDark;
    final provider = context.watch<PlaylistProvider>();
    final library = context.watch<LibraryProvider>();
    final playlist = provider.byId(playlistId);
    if (playlist == null) {
      return const Scaffold(body: DotEmptyState(artId: DotEmptyState.ghost, title: 'Playlist deleted'));
    }
    final songs = provider.songsOf(playlist, library.songs);
    final total = songs.fold<int>(0, (n, s) => n + s.duration);
    final source = 'Playlist · ${playlist.name}';
    final player = context.read<PlayerProvider>();

    Future<void> menu(String action) async {
      if (action == 'rename') {
        final name = await askPlaylistName(context, title: 'Rename playlist', initial: playlist.name);
        if (name != null) await provider.rename(playlist.id, name);
      } else if (action == 'delete') {
        final ok = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            backgroundColor: c.surface,
            title: Text('DELETE PLAYLIST', style: NothingTypography.monoLabel(isDark: c.dark)),
            content: Text('Delete "${playlist.name}"? Your songs stay on the device.',
                style: NothingTypography.bodyMedium(isDark: c.dark)),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('CANCEL')),
              TextButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text('DELETE', style: TextStyle(color: NothingColors.signalRedBright)),
              ),
            ],
          ),
        );
        if (ok == true) {
          await provider.delete(playlist.id);
          if (context.mounted) Navigator.pop(context);
        }
      }
    }

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        title: Text('PLAYLIST', style: NothingTypography.displayTitle(isDark: isDark, fontSize: 16)),
        actions: [
          PopupMenuButton<String>(
            onSelected: menu,
            itemBuilder: (_) => const [
              PopupMenuItem(value: 'rename', child: Text('Rename')),
              PopupMenuItem(value: 'delete', child: Text('Delete')),
            ],
          ),
        ],
      ),
      body: Column(
        children: [
          CollectionHeader(
            kicker: 'Playlist',
            title: playlist.name,
            subtitle: '${songs.length} tracks · ${Formatters.formatMs(total)}',
            artworkSongId: songs.isEmpty ? null : songs.first.id,
            artwork: songs.isEmpty ? const DotArt(artId: DotEmptyState.note, size: 112) : null,
            onPlay: songs.isEmpty ? null : () => player.playSong(songs.first, songs, source: source),
            onShuffle: songs.isEmpty
                ? null
                : () {
                    final shuffled = [...songs]..shuffle();
                    player.playSong(shuffled.first, shuffled, source: source);
                  },
          ),
          Expanded(
            child: songs.isEmpty
                ? const DotEmptyState(
                    artId: DotEmptyState.note,
                    title: 'Empty playlist',
                    hint: 'Long-press any song and choose Add to playlist.',
                  )
                : ReorderableListView.builder(
                    buildDefaultDragHandles: false,
                    padding: const EdgeInsets.only(bottom: 16),
                    itemCount: songs.length,
                    onReorderItem: (o, n) {
                      HapticFeedback.selectionClick();
                      provider.moveSong(playlist.id, o, n);
                    },
                    itemBuilder: (context, i) {
                      final song = songs[i];
                      return Dismissible(
                        key: ValueKey('pl_${song.id}_$i'),
                        direction: DismissDirection.endToStart,
                        background: Container(
                          color: NothingColors.signalRed,
                          alignment: Alignment.centerRight,
                          padding: const EdgeInsets.symmetric(horizontal: 24),
                          child: const Text('REMOVE', style: TextStyle(fontFamily: NothingTypography.fontMono, color: Colors.white)),
                        ),
                        onDismissed: (_) async {
                          HapticFeedback.lightImpact();
                          // by id: rows skip songs whose files are gone
                          final at = playlist.songIds.indexOf(song.id);
                          await provider.removeSongAt(playlist.id, at);
                          if (!context.mounted) return;
                          showDotToast(
                            context,
                            'Removed · ${song.title}',
                            actionLabel: 'Undo',
                            onAction: () async {
                              final p = provider.byId(playlist.id);
                              if (p == null) return;
                              final ids = [...p.songIds]..insert(at.clamp(0, p.songIds.length), song.id);
                              await provider.restoreIds(playlist.id, ids);
                            },
                          );
                        },
                        child: Row(
                          children: [
                            Expanded(child: SongTile(song: song, playlist: songs, source: source)),
                            ReorderableDragStartListener(
                              index: i,
                              child: Padding(
                                padding: const EdgeInsets.all(12),
                                child: Icon(Icons.drag_handle, color: c.secondary),
                              ),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
          ),
          const MiniPlayer(),
        ],
      ),
    );
  }
}
