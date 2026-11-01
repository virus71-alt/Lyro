import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../core/utils/formatters.dart';
import '../../../data/models/song_model.dart';
import '../../../shared/widgets/collection_header.dart';
import '../../../shared/widgets/dot_matrix.dart';
import '../../player/providers/player_provider.dart';
import '../providers/library_provider.dart';
import '../widgets/mini_player.dart';
import '../widgets/song_tile.dart';

enum SmartList {
  mostPlayed('Most played', 'Your top tracks by play count', DotEmptyState.spark),
  recentlyAdded('Recently added', 'Newest files on this device', 6), // equalizer art
  forgotten('Not played in a while', 'Songs you haven\'t heard for a month', DotEmptyState.vinyl);

  final String title;
  final String description;
  final int artId;
  const SmartList(this.title, this.description, this.artId);

  static const int _limit = 50;

  List<Song> build(List<Song> library, Map<int, ({int count, int lastPlayed})> stats) {
    switch (this) {
      case SmartList.mostPlayed:
        final played = library.where((s) => (stats[s.id]?.count ?? 0) > 0).toList()
          ..sort((a, b) => stats[b.id]!.count.compareTo(stats[a.id]!.count));
        return played.take(_limit).toList();
      case SmartList.recentlyAdded:
        final withDate = library.where((s) => (s.dateAdded ?? 0) > 0).toList()
          ..sort((a, b) => b.dateAdded!.compareTo(a.dateAdded!));
        return withDate.take(_limit).toList();
      case SmartList.forgotten:
        final cutoff = DateTime.now().subtract(const Duration(days: 30)).millisecondsSinceEpoch;
        final stale = library
            .where((s) => stats[s.id] != null && stats[s.id]!.lastPlayed < cutoff)
            .toList()
          ..sort((a, b) => stats[a.id]!.lastPlayed.compareTo(stats[b.id]!.lastPlayed));
        return stale.take(_limit).toList();
    }
  }
}

class SmartListScreen extends StatelessWidget {
  final SmartList kind;
  const SmartListScreen({super.key, required this.kind});

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final library = context.watch<LibraryProvider>();
    final songs = kind.build(library.songs, library.playStats);
    final stats = library.playStats;
    final player = context.read<PlayerProvider>();
    final source = kind.title;
    final total = songs.fold<int>(0, (n, s) => n + s.duration);

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        title: Text('SMART LIST', style: NothingTypography.displayTitle(isDark: isDark, fontSize: 16)),
      ),
      body: Column(
        children: [
          CollectionHeader(
            kicker: kind.description,
            title: kind.title,
            subtitle: '${songs.length} tracks · ${Formatters.formatMs(total)}',
            artworkSongId: songs.isEmpty ? null : songs.first.id,
            artwork: songs.isEmpty ? DotArt(artId: kind.artId, size: 112) : null,
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
                ? DotEmptyState(
                    artId: kind.artId,
                    title: 'Nothing here yet',
                    hint: kind == SmartList.recentlyAdded
                        ? 'No date information for your files.'
                        : 'Keep listening — this list fills itself.',
                  )
                : ListView.builder(
                    padding: const EdgeInsets.only(bottom: 16),
                    itemCount: songs.length,
                    itemBuilder: (context, i) {
                      final s = songs[i];
                      final tile = SongTile(song: s, playlist: songs, source: source);
                      if (kind != SmartList.mostPlayed) return tile;
                      return Stack(
                        children: [
                          tile,
                          Positioned(
                            right: 52,
                            top: 0,
                            bottom: 0,
                            child: Center(
                              child: Text(
                                '${stats[s.id]?.count ?? 0}×',
                                style: NothingTypography.dottedBold(
                                  isDark: isDark,
                                  fontSize: 14,
                                  color: NothingColors.signalRedBright,
                                ),
                              ),
                            ),
                          ),
                        ],
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
