import 'package:flutter/material.dart';
import '../../../shared/widgets/dot_matrix.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../shared/widgets/nothing_section_header.dart';
import '../../../shared/widgets/nothing_text_field.dart';
import '../../library/providers/library_provider.dart';
import '../../library/screens/album_detail_screen.dart';
import '../../library/screens/artist_detail_screen.dart';
import '../../library/widgets/artist_tile.dart';
import '../../library/widgets/mini_player.dart';
import '../../library/widgets/song_tile.dart';

class SearchScreen extends StatefulWidget {
  const SearchScreen({super.key});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final library = context.watch<LibraryProvider>();

    final q = _query.toLowerCase();
    final matchingSongs = _query.isEmpty
        ? []
        : library.songs.where((s) => s.title.toLowerCase().contains(q) || s.artist.toLowerCase().contains(q)).toList();

    final matchingAlbums = _query.isEmpty
        ? []
        : library.albums.where((a) => a.album.toLowerCase().contains(q) || a.artist.toLowerCase().contains(q)).toList();

    final matchingArtists = _query.isEmpty
        ? []
        : library.artists.where((a) => a.artist.toLowerCase().contains(q)).toList();

    final hasResults = matchingSongs.isNotEmpty || matchingAlbums.isNotEmpty || matchingArtists.isNotEmpty;

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        title: Text(
          'SEARCH',
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
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                child: NothingTextField(
                  controller: _searchCtrl,
                  hintText: 'SEARCH SONGS, ARTISTS, ALBUMS...',
                  autofocus: true,
                  prefixIcon: Icon(
                    Icons.search,
                    size: 18,
                    color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                  ),
                  onChanged: (val) {
                    setState(() {
                      _query = val.trim();
                    });
                  },
                  onClear: () {
                    _searchCtrl.clear();
                    setState(() {
                      _query = '';
                    });
                  },
                ),
              ),
              Expanded(
                child: _query.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              Icons.search,
                              size: 48,
                              color: isDark ? NothingColors.darkLine2 : NothingColors.lightLine2,
                            ),
                            const SizedBox(height: 12),
                            Text(
                              'TYPE TO SEARCH LOCAL AUDIO',
                              style: NothingTypography.monoLabel(isDark: isDark),
                            ),
                          ],
                        ),
                      )
                    : !hasResults
                        ? DotEmptyState(artId: DotEmptyState.ghost, title: 'No results', hint: 'Nothing in your library matches that.')
                        : ListView(
                            padding: const EdgeInsets.symmetric(vertical: 8.0),
                            children: [
                              // Songs
                              if (matchingSongs.isNotEmpty) ...[
                                Padding(
                                  padding: const EdgeInsets.symmetric(horizontal: 16.0),
                                  child: NothingSectionHeader(
                                    title: 'SONGS',
                                    count: '${matchingSongs.length}',
                                  ),
                                ),
                                ...matchingSongs.take(15).map(
                                      (s) => SongTile(
                                        song: s,
                                        playlist: matchingSongs.cast(),
                                        source: 'Search',
                                      ),
                                    ),
                              ],
                              // Albums
                              if (matchingAlbums.isNotEmpty) ...[
                                const SizedBox(height: 16),
                                Padding(
                                  padding: const EdgeInsets.symmetric(horizontal: 16.0),
                                  child: NothingSectionHeader(
                                    title: 'ALBUMS',
                                    count: '${matchingAlbums.length}',
                                  ),
                                ),
                                ...matchingAlbums.map(
                                  (album) => ListTile(
                                    title: Text(
                                      album.album,
                                      style: TextStyle(
                                        fontFamily: NothingTypography.fontUi,
                                        fontSize: 14,
                                        fontWeight: FontWeight.w600,
                                        color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                      ),
                                    ),
                                    subtitle: Text(
                                      '${album.artist} • ${album.numOfSongs} TRACKS',
                                      style: NothingTypography.bodySmall(isDark: isDark),
                                    ),
                                    trailing: const Icon(Icons.chevron_right, size: 18),
                                    onTap: () {
                                      Navigator.of(context).push(
                                        MaterialPageRoute(
                                          builder: (_) => AlbumDetailScreen(album: album),
                                        ),
                                      );
                                    },
                                  ),
                                ),
                              ],
                              // Artists
                              if (matchingArtists.isNotEmpty) ...[
                                const SizedBox(height: 16),
                                Padding(
                                  padding: const EdgeInsets.symmetric(horizontal: 16.0),
                                  child: NothingSectionHeader(
                                    title: 'ARTISTS',
                                    count: '${matchingArtists.length}',
                                  ),
                                ),
                                ...matchingArtists.map(
                                  (artist) => ArtistTile(
                                    artist: artist,
                                    onTap: () {
                                      Navigator.of(context).push(
                                        MaterialPageRoute(
                                          builder: (_) => ArtistDetailScreen(artist: artist),
                                        ),
                                      );
                                    },
                                  ),
                                ),
                              ],
                            ],
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

