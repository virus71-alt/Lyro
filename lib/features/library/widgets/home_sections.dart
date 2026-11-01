import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_palette.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../data/models/song_model.dart';
import '../../../shared/widgets/dot_matrix.dart';
import '../../player/providers/player_provider.dart';
import '../../player/widgets/track_artwork.dart';
import '../../profile/providers/profile_provider.dart';
import 'song_tile.dart';

typedef PlayStats = Map<int, ({int count, int lastPlayed})>;

/// Picks for Home, stable for the day (seeded by date) so the page doesn't
/// reshuffle on every rebuild, but fresh each morning.
class HomePicks {
  HomePicks._();

  static int get _daySeed {
    final now = DateTime.now();
    return now.year * 1000 + now.month * 40 + now.day;
  }

  /// Recent + most played + daily random, interleaved, without repeats.
  static List<Song> quickPicks(List<Song> library, List<Song> recent, PlayStats stats, {int count = 20}) {
    final mostPlayed = [...library.where((s) => (stats[s.id]?.count ?? 0) > 0)]
      ..sort((a, b) => stats[b.id]!.count.compareTo(stats[a.id]!.count));
    final random = [...library]..shuffle(math.Random(_daySeed));
    final sources = [recent.take(8).toList(), mostPlayed.take(8).toList(), random];
    final seen = <String>{};
    final out = <Song>[];
    var i = 0;
    while (out.length < count && sources.any((s) => i < s.length)) {
      for (final src in sources) {
        if (i < src.length && seen.add(src[i].path)) out.add(src[i]);
        if (out.length >= count) break;
      }
      i++;
    }
    return out;
  }

  /// Most played first, topped up with recent and daily random songs.
  static List<Song> speedDial(List<Song> library, List<Song> recent, PlayStats stats, {int count = 18}) {
    final mostPlayed = [...library.where((s) => (stats[s.id]?.count ?? 0) > 0)]
      ..sort((a, b) => stats[b.id]!.count.compareTo(stats[a.id]!.count));
    final random = [...library]..shuffle(math.Random(_daySeed + 7));
    final seen = <String>{};
    return [
      for (final s in [...mostPlayed, ...recent, ...random])
        if (seen.add(s.path)) s,
    ].take(count).toList();
  }
}

/// Section title in the Home style (big, bold), with an optional action.
class HomeSectionTitle extends StatelessWidget {
  final String title;
  final String? kicker;
  final Widget? leading;
  final Widget? trailing;
  const HomeSectionTitle({super.key, required this.title, this.kicker, this.leading, this.trailing});

  @override
  Widget build(BuildContext context) {
    final c = NothingPalette.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 0, 16, 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          if (leading != null) ...[leading!, const SizedBox(width: 12)],
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (kicker != null)
                  Text(
                    kicker!.toUpperCase(),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontFamily: NothingTypography.fontMono,
                      fontSize: 11,
                      letterSpacing: 1.2,
                      color: c.secondary,
                    ),
                  ),
                Text(
                  title,
                  style: TextStyle(
                    fontFamily: NothingTypography.fontUi,
                    fontSize: 24,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.4,
                    color: c.display,
                  ),
                ),
              ],
            ),
          ),
          ?trailing,
        ],
      ),
    );
  }
}

/// Small dot page indicator.
class _PageDots extends StatelessWidget {
  final int count;
  final int index;
  const _PageDots({required this.count, required this.index});

  @override
  Widget build(BuildContext context) {
    final c = NothingPalette.of(context);
    if (count <= 1) return const SizedBox(height: 8);
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        for (var i = 0; i < count; i++)
          AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            margin: const EdgeInsets.symmetric(horizontal: 3),
            width: 6,
            height: 6,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: i == index ? c.display : c.display.withValues(alpha: 0.25),
            ),
          ),
      ],
    );
  }
}

// =============================================================================
// QUICK PICKS
// =============================================================================

/// Columns of four song rows that page sideways (next column peeks in).
class QuickPicksSection extends StatelessWidget {
  final List<Song> songs;
  final PlayStats stats;
  const QuickPicksSection({super.key, required this.songs, required this.stats});

  static const _rowsPerColumn = 4;
  static const _rowHeight = 64.0;

  @override
  Widget build(BuildContext context) {
    if (songs.isEmpty) return const SizedBox.shrink();
    final c = NothingPalette.of(context);
    final player = context.read<PlayerProvider>();
    final currentPath = context.select<PlayerProvider, String?>((p) => p.currentSong?.path);
    final columns = (songs.length / _rowsPerColumn).ceil();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        HomeSectionTitle(
          title: 'Quick picks',
          trailing: OutlinedButton(
            key: const Key('quick_picks_play_all'),
            onPressed: () {
              HapticFeedback.lightImpact();
              player.playSong(songs.first, songs, source: 'Quick picks');
            },
            style: OutlinedButton.styleFrom(
              foregroundColor: c.display,
              side: BorderSide(color: c.display.withValues(alpha: 0.35)),
              shape: const StadiumBorder(),
              padding: const EdgeInsets.symmetric(horizontal: 16),
              visualDensity: VisualDensity.compact,
            ),
            child: const Text(
              'Play all',
              style: TextStyle(fontFamily: NothingTypography.fontUi, fontWeight: FontWeight.w600),
            ),
          ),
        ),
        SizedBox(
          height: _rowHeight * _rowsPerColumn,
          child: PageView.builder(
            key: const Key('quick_picks_pages'),
            controller: PageController(viewportFraction: 0.9),
            padEnds: false,
            itemCount: columns,
            itemBuilder: (context, col) => Column(
              children: [
                for (var r = 0; r < _rowsPerColumn; r++)
                  if (col * _rowsPerColumn + r < songs.length)
                    _QuickPickRow(
                      song: songs[col * _rowsPerColumn + r],
                      plays: stats[songs[col * _rowsPerColumn + r].id]?.count ?? 0,
                      isCurrent: songs[col * _rowsPerColumn + r].path == currentPath,
                      onTap: () => player.playSong(
                        songs[col * _rowsPerColumn + r],
                        songs,
                        source: 'Quick picks',
                      ),
                      onMore: (ctx) => SongTile(
                        song: songs[col * _rowsPerColumn + r],
                        playlist: songs,
                        source: 'Quick picks',
                      ).showOptions(ctx),
                    ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _QuickPickRow extends StatelessWidget {
  final Song song;
  final int plays;
  final bool isCurrent;
  final VoidCallback onTap;
  final void Function(BuildContext) onMore;

  const _QuickPickRow({
    required this.song,
    required this.plays,
    required this.isCurrent,
    required this.onTap,
    required this.onMore,
  });

  @override
  Widget build(BuildContext context) {
    final c = NothingPalette.of(context);
    final subtitle = plays > 0 ? '${song.artist} • $plays ${plays == 1 ? 'play' : 'plays'}' : song.artist;
    return SizedBox(
      height: QuickPicksSection._rowHeight,
      child: InkWell(
        onTap: () {
          HapticFeedback.selectionClick();
          onTap();
        },
        onLongPress: () => onMore(context),
        child: Padding(
          padding: const EdgeInsets.only(left: 20),
          child: Row(
            children: [
              SizedBox(
                width: 48,
                height: 48,
                child: Stack(
                  children: [
                    TrackArtwork(songId: song.id, title: song.title, size: 48, radius: 4),
                    if (isCurrent)
                      Container(
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.5),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        alignment: Alignment.center,
                        child: const Icon(Icons.graphic_eq, size: 22, color: NothingColors.signalRedBright),
                      ),
                  ],
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      song.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontFamily: NothingTypography.fontUi,
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: c.display,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 13, color: c.secondary),
                    ),
                  ],
                ),
              ),
              IconButton(
                tooltip: 'More',
                icon: Icon(Icons.more_vert, color: c.secondary),
                onPressed: () => onMore(context),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// =============================================================================
// SPEED DIAL
// =============================================================================

/// 3×3 pages of square art tiles for your most-played songs.
class SpeedDialSection extends StatefulWidget {
  final List<Song> songs;
  const SpeedDialSection({super.key, required this.songs});

  @override
  State<SpeedDialSection> createState() => _SpeedDialSectionState();
}

class _SpeedDialSectionState extends State<SpeedDialSection> {
  static const _perPage = 9;
  int _page = 0;

  @override
  Widget build(BuildContext context) {
    final songs = widget.songs;
    if (songs.isEmpty) return const SizedBox.shrink();
    final profile = context.watch<ProfileProvider>();
    final player = context.read<PlayerProvider>();
    final currentPath = context.select<PlayerProvider, String?>((p) => p.currentSong?.path);
    final pages = (songs.length / _perPage).ceil();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        HomeSectionTitle(
          kicker: profile.name,
          title: 'Speed dial',
          leading: NothingAvatar(avatarId: profile.avatarId, name: profile.name, size: 40),
        ),
        LayoutBuilder(
          builder: (context, constraints) {
            const gap = 6.0;
            const side = 16.0;
            final tile = (constraints.maxWidth - side * 2 - gap * 2) / 3;
            return SizedBox(
              height: tile * 3 + gap * 2,
              child: PageView.builder(
                key: const Key('speed_dial_pages'),
                itemCount: pages,
                onPageChanged: (p) => setState(() => _page = p),
                itemBuilder: (context, page) => Padding(
                  padding: const EdgeInsets.symmetric(horizontal: side),
                  child: Wrap(
                    spacing: gap,
                    runSpacing: gap,
                    children: [
                      for (var i = page * _perPage; i < math.min(songs.length, (page + 1) * _perPage); i++)
                        _SpeedDialTile(
                          song: songs[i],
                          size: tile,
                          isCurrent: songs[i].path == currentPath,
                          onTap: () {
                            HapticFeedback.selectionClick();
                            player.playSong(songs[i], songs, source: 'Speed dial');
                          },
                          onLongPress: () =>
                              SongTile(song: songs[i], playlist: songs, source: 'Speed dial').showOptions(context),
                        ),
                    ],
                  ),
                ),
              ),
            );
          },
        ),
        const SizedBox(height: 10),
        _PageDots(count: pages, index: _page),
      ],
    );
  }
}

class _SpeedDialTile extends StatelessWidget {
  final Song song;
  final double size;
  final bool isCurrent;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  const _SpeedDialTile({
    required this.song,
    required this.size,
    required this.isCurrent,
    required this.onTap,
    required this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final c = NothingPalette.of(context);
    return GestureDetector(
      onTap: onTap,
      onLongPress: onLongPress,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        width: size,
        height: size,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: isCurrent ? c.display : Colors.transparent,
            width: 2.5,
          ),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(isCurrent ? 7.5 : 10),
          child: Stack(
            fit: StackFit.expand,
            children: [
              TrackArtwork(songId: song.id, title: song.title, size: size, radius: 0),
              // Bottom scrim for the title
              const DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [Colors.transparent, Colors.transparent, Color(0xCC000000)],
                    stops: [0, 0.55, 1],
                  ),
                ),
              ),
              Positioned(
                left: 8,
                right: 8,
                bottom: 7,
                child: Text(
                  song.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontFamily: NothingTypography.fontUi,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: Colors.white,
                  ),
                ),
              ),
              if (isCurrent)
                const Positioned(
                  top: 6,
                  right: 6,
                  child: Icon(Icons.graphic_eq, size: 18, color: NothingColors.signalRedBright),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
