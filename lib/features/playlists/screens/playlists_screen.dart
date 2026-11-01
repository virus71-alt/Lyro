import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../shared/widgets/dot_matrix.dart';
import '../../library/providers/library_provider.dart';
import '../../library/widgets/folder_tile.dart' show DottedRule;
import '../../library/widgets/mini_player.dart';
import '../../player/widgets/track_artwork.dart';
import '../providers/playlist_provider.dart';
import '../widgets/add_to_playlist_sheet.dart';
import 'playlist_detail_screen.dart';

class PlaylistsScreen extends StatelessWidget {
  const PlaylistsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final provider = context.watch<PlaylistProvider>();
    final library = context.watch<LibraryProvider>();
    final playlists = provider.playlists;

    Future<void> create() async {
      final name = await askPlaylistName(context);
      if (name == null) return;
      HapticFeedback.lightImpact();
      final p = await provider.create(name);
      if (context.mounted) {
        Navigator.of(context).push(MaterialPageRoute(builder: (_) => PlaylistDetailScreen(playlistId: p.id)));
      }
    }

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        title: Text('PLAYLISTS', style: NothingTypography.displayTitle(isDark: isDark, fontSize: 18)),
        actions: [
          IconButton(
            key: const Key('create_playlist'),
            icon: const Icon(Icons.add),
            tooltip: 'New playlist',
            onPressed: create,
          ),
        ],
      ),
      body: Stack(
        fit: StackFit.expand,
        children: [
          Positioned.fill(
            child: CustomPaint(painter: NothingDotPainter(isDark: isDark, spacing: 24.0, dotRadius: 1.0)),
          ),
          Column(
            children: [
              Expanded(
                child: playlists.isEmpty
                    ? DotEmptyState(
                        artId: DotEmptyState.note,
                        title: 'No playlists yet',
                        hint: 'Create one here, or long-press any song and choose Add to playlist.',
                        action: FilledButton(
                          onPressed: create,
                          style: FilledButton.styleFrom(
                            backgroundColor: isDark ? Colors.white : Colors.black,
                            foregroundColor: isDark ? Colors.black : Colors.white,
                          ),
                          child: const Text('NEW PLAYLIST', style: TextStyle(fontFamily: NothingTypography.fontMono)),
                        ),
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.fromLTRB(20, 4, 20, 16),
                        itemCount: playlists.length,
                        itemBuilder: (context, i) {
                          final p = playlists[i];
                          final songs = provider.songsOf(p, library.songs);
                          return InkWell(
                            onTap: () => Navigator.of(context).push(
                              MaterialPageRoute(builder: (_) => PlaylistDetailScreen(playlistId: p.id)),
                            ),
                            child: Column(
                              children: [
                                Padding(
                                  padding: const EdgeInsets.symmetric(vertical: 14),
                                  child: Row(
                                    children: [
                                      SizedBox(
                                        width: 50,
                                        height: 50,
                                        child: songs.isEmpty
                                            ? const DotArt(artId: DotEmptyState.note, size: 50)
                                            : TrackArtwork(songId: songs.first.id, title: p.name, size: 50, radius: 6),
                                      ),
                                      const SizedBox(width: 14),
                                      Expanded(
                                        child: Text(
                                          p.name,
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: TextStyle(
                                            fontFamily: NothingTypography.fontUi,
                                            fontSize: 15,
                                            fontWeight: FontWeight.w600,
                                            color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                          ),
                                        ),
                                      ),
                                      Column(
                                        crossAxisAlignment: CrossAxisAlignment.end,
                                        children: [
                                          Text(
                                            songs.length.toString().padLeft(2, '0'),
                                            style: NothingTypography.dottedBold(isDark: isDark, fontSize: 22),
                                          ),
                                          Text(
                                            'TRACKS',
                                            style: TextStyle(
                                              fontFamily: NothingTypography.fontMono,
                                              fontSize: 8.5,
                                              letterSpacing: 1.2,
                                              color: isDark ? NothingColors.darkMuted : NothingColors.lightMuted,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ],
                                  ),
                                ),
                                DottedRule(isDark: isDark),
                              ],
                            ),
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
