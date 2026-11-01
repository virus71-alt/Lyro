import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../data/repositories/music_repository.dart';
import '../../../shared/widgets/nothing_button.dart';
import '../../../shared/widgets/nothing_text_field.dart';
import '../../player/providers/player_provider.dart';
import '../providers/library_provider.dart';
import '../widgets/mini_player.dart';
import '../widgets/song_tile.dart';

class AllSongsScreen extends StatefulWidget {
  const AllSongsScreen({super.key});

  @override
  State<AllSongsScreen> createState() => _AllSongsScreenState();
}

class _AllSongsScreenState extends State<AllSongsScreen> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _filterQuery = '';

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final library = context.watch<LibraryProvider>();

    var songs = library.songs;
    if (_filterQuery.isNotEmpty) {
      songs = songs.where((s) {
        final q = _filterQuery.toLowerCase();
        return s.title.toLowerCase().contains(q) ||
            s.artist.toLowerCase().contains(q) ||
            s.album.toLowerCase().contains(q);
      }).toList();
    }

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        title: Text(
          'ALL SONGS',
          style: NothingTypography.displayTitle(isDark: isDark, fontSize: 18),
        ),
        actions: [
          IconButton(
            icon: Icon(
              Icons.sort,
              size: 20,
              color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
            ),
            onPressed: () => _showSortModal(context, library),
            tooltip: 'Sort songs',
          ),
        ],
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
              // Search & Filter header
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                child: NothingTextField(
                  controller: _searchCtrl,
                  hintText: 'FILTER SONGS...',
                  prefixIcon: Icon(
                    Icons.search,
                    size: 18,
                    color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                  ),
                  onChanged: (val) {
                    setState(() {
                      _filterQuery = val.trim();
                    });
                  },
                  onClear: () {
                    _searchCtrl.clear();
                    setState(() {
                      _filterQuery = '';
                    });
                  },
                ),
              ),
              // Play All & Shuffle Buttons
              if (songs.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 6.0),
                  child: Row(
                    children: [
                      Text(
                        '${songs.length} TRACKS',
                        style: NothingTypography.monoLabel(isDark: isDark),
                      ),
                      const Spacer(),
                      NothingButton(
                        text: 'SHUFFLE ALL',
                        variant: NothingButtonVariant.secondary,
                        icon: const Icon(Icons.shuffle, size: 14),
                        onPressed: () {
                          final shuffled = List.of(songs)..shuffle();
                          if (shuffled.isNotEmpty) {
                            context.read<PlayerProvider>().playSong(shuffled.first, shuffled);
                          }
                        },
                      ),
                    ],
                  ),
                ),
              const Divider(height: 1),
              // Songs List
              Expanded(
                child: songs.isEmpty
                    ? Center(
                        child: Text(
                          _filterQuery.isNotEmpty ? 'NO MATCHING TRACKS' : 'NO SONGS FOUND',
                          style: NothingTypography.monoLabel(isDark: isDark),
                        ),
                      )
                    : ListView.builder(
                        itemCount: songs.length,
                        itemBuilder: (context, index) {
                          return SongTile(
                            song: songs[index],
                            playlist: songs,
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

  void _showSortModal(BuildContext context, LibraryProvider library) {
    final isDark = context.isDark;

    showModalBottomSheet(
      context: context,
      backgroundColor: isDark ? NothingColors.darkSurface : NothingColors.lightSurface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(NothingColors.radiusMd)),
      ),
      builder: (ctx) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 16.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20.0, vertical: 8.0),
                  child: Text(
                    'SORT TRACKS BY',
                    style: NothingTypography.monoLabel(isDark: isDark),
                  ),
                ),
                _sortTile(ctx, library, 'TITLE (A-Z)', SongSort.titleAsc),
                _sortTile(ctx, library, 'TITLE (Z-A)', SongSort.titleDesc),
                _sortTile(ctx, library, 'ARTIST (A-Z)', SongSort.artistAsc),
                _sortTile(ctx, library, 'ALBUM (A-Z)', SongSort.albumAsc),
                _sortTile(ctx, library, 'DURATION (LONGEST)', SongSort.durationDesc),
                _sortTile(ctx, library, 'DATE ADDED (NEWEST)', SongSort.dateAddedDesc),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _sortTile(BuildContext ctx, LibraryProvider library, String label, SongSort sort) {
    final isSelected = library.currentSort == sort;
    final isDark = ctx.isDark;

    return ListTile(
      title: Text(
        label,
        style: TextStyle(
          fontFamily: NothingTypography.fontMono,
          fontSize: 12,
          fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
          color: isSelected
              ? NothingColors.darkAccentText
              : (isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary),
        ),
      ),
      trailing: isSelected
          ? const Icon(Icons.check, size: 18, color: NothingColors.darkAccentText)
          : null,
      onTap: () {
        library.setSort(sort);
        Navigator.pop(ctx);
      },
    );
  }
}
