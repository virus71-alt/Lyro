import 'package:flutter/material.dart';
import 'package:on_audio_query/on_audio_query.dart';
import 'package:provider/provider.dart';
import '../../../core/constants/app_constants.dart';
import '../../../core/permissions/permission_service.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../shared/widgets/nothing_button.dart';
import '../../../shared/widgets/nothing_card.dart';
import '../../../shared/widgets/nothing_section_header.dart';
import '../../player/providers/player_provider.dart';
import '../../player/widgets/track_artwork.dart';
import '../../search/screens/search_screen.dart';
import '../../settings/screens/settings_screen.dart';
import '../providers/library_provider.dart';
import '../widgets/mini_player.dart';
import '../widgets/song_tile.dart';
import 'album_detail_screen.dart';
import 'albums_screen.dart';
import 'all_songs_screen.dart';
import 'artists_screen.dart';
import 'folders_screen.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final library = context.watch<LibraryProvider>();

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      body: Stack(
        fit: StackFit.expand,
        children: [
          // Background Dot Grid
          Positioned.fill(
            child: CustomPaint(
              painter: NothingDotPainter(
                isDark: isDark,
                spacing: 24.0,
                dotRadius: 1.0,
              ),
            ),
          ),
          SafeArea(
            child: Column(
              children: [
                // Top Header / Branding & Search
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20.0, vertical: 12.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          Text(
                            AppConstants.appName.toUpperCase(),
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
                      ),
                      Row(
                        children: [
                          IconButton(
                            icon: Icon(
                              Icons.search,
                              size: 22,
                              color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                            ),
                            onPressed: () {
                              Navigator.of(context).push(
                                MaterialPageRoute(builder: (_) => const SearchScreen()),
                              );
                            },
                            tooltip: 'Search',
                          ),
                          IconButton(
                            icon: Icon(
                              Icons.settings_outlined,
                              size: 22,
                              color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                            ),
                            onPressed: () {
                              Navigator.of(context).push(
                                MaterialPageRoute(builder: (_) => const SettingsScreen()),
                              );
                            },
                            tooltip: 'Settings',
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                // Main Scrollable Area
                Expanded(
                  child: RefreshIndicator(
                    onRefresh: () => library.loadLibrary(isRescan: true),
                    color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                    backgroundColor: isDark ? NothingColors.darkRaised : NothingColors.lightRaised,
                    child: ListView(
                      padding: const EdgeInsets.symmetric(horizontal: 20.0),
                      children: [
                        // Permission Banner if needed
                        if (!library.isPermissionGranted)
                          _buildPermissionBanner(context, isDark, library),

                        // Loading state
                        if (library.isLoading)
                          Padding(
                            padding: const EdgeInsets.symmetric(vertical: 40.0),
                            child: Center(
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  CircularProgressIndicator(
                                    strokeWidth: 2,
                                    valueColor: AlwaysStoppedAnimation<Color>(
                                      isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                    ),
                                  ),
                                  const SizedBox(height: 16),
                                  Text(
                                    'SCANNING MUSIC STORAGE...',
                                    style: NothingTypography.monoLabel(isDark: isDark),
                                  ),
                                ],
                              ),
                            ),
                          )
                        else if (library.songs.isEmpty && library.isPermissionGranted)
                          _buildEmptyState(context, isDark, library)
                        else ...[
                          // Quick Categories Grid
                          const SizedBox(height: 12),
                          _buildCategoryGrid(context, isDark, library),

                          // Recently Played Section
                          if (library.recentlyPlayedSongs.isNotEmpty) ...[
                            const SizedBox(height: 28),
                            NothingSectionHeader(
                              title: 'RECENTLY PLAYED',
                              count: '${library.recentlyPlayedSongs.length}',
                            ),
                            const SizedBox(height: 8),
                            SizedBox(
                              height: 160,
                              child: ListView.separated(
                                scrollDirection: Axis.horizontal,
                                itemCount: library.recentlyPlayedSongs.take(10).length,
                                separatorBuilder: (_, _) => const SizedBox(width: 12),
                                itemBuilder: (context, index) {
                                  final song = library.recentlyPlayedSongs[index];
                                  return _buildRecentCard(context, isDark, song, library.recentlyPlayedSongs);
                                },
                              ),
                            ),
                          ],

                          // Favorites Section
                          if (library.favoriteSongs.isNotEmpty) ...[
                            const SizedBox(height: 28),
                            NothingSectionHeader(
                              title: 'FAVORITES',
                              count: '${library.favoriteSongs.length}',
                            ),
                            ...library.favoriteSongs.take(5).map(
                                  (s) => SongTile(
                                    song: s,
                                    playlist: library.favoriteSongs,
                                  ),
                                ),
                          ],

                          // Top Discovered Albums
                          if (library.albums.isNotEmpty) ...[
                            const SizedBox(height: 28),
                            NothingSectionHeader(
                              title: 'DISCOVERED ALBUMS',
                              count: '${library.albums.length}',
                              trailing: TextButton(
                                onPressed: () {
                                  Navigator.of(context).push(
                                    MaterialPageRoute(builder: (_) => const AlbumsScreen()),
                                  );
                                },
                                child: Text(
                                  'VIEW ALL',
                                  style: NothingTypography.monoLabel(
                                    isDark: isDark,
                                    color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                  ),
                                ),
                              ),
                            ),
                            const SizedBox(height: 8),
                            SizedBox(
                              height: 176,
                              child: ListView.separated(
                                scrollDirection: Axis.horizontal,
                                itemCount: library.albums.take(8).length,
                                separatorBuilder: (_, _) => const SizedBox(width: 14),
                                itemBuilder: (context, index) {
                                  final album = library.albums[index];
                                  final resolvedSongId = album.songId ??
                                      library.getArtworkSongIdForAlbum(album.id, album.album, album.artist);
                                  return GestureDetector(
                                    onTap: () {
                                      Navigator.of(context).push(
                                        MaterialPageRoute(
                                          builder: (_) => AlbumDetailScreen(album: album),
                                        ),
                                      );
                                    },
                                    child: SizedBox(
                                      width: 110,
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          TrackArtwork(
                                            songId: resolvedSongId ?? album.id,
                                            title: album.album,
                                            type: resolvedSongId != null ? ArtworkType.AUDIO : ArtworkType.ALBUM,
                                            size: 110,
                                            radius: NothingColors.radiusSm,
                                          ),
                                          const SizedBox(height: 8),
                                          Text(
                                            album.album,
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                            style: TextStyle(
                                              fontFamily: NothingTypography.fontUi,
                                              fontSize: 12,
                                              fontWeight: FontWeight.w600,
                                              color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                            ),
                                          ),
                                          const SizedBox(height: 2),
                                          Text(
                                            album.artist,
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                            style: TextStyle(
                                              fontFamily: NothingTypography.fontUi,
                                              fontSize: 10,
                                              color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                  );
                                },
                              ),
                            ),
                          ],

                          // All Songs Preview
                          const SizedBox(height: 28),
                          NothingSectionHeader(
                            title: 'SONGS',
                            count: '${library.songs.length}',
                            trailing: TextButton(
                              onPressed: () {
                                Navigator.of(context).push(
                                  MaterialPageRoute(builder: (_) => const AllSongsScreen()),
                                );
                              },
                              child: Text(
                                'VIEW ALL',
                                style: NothingTypography.monoLabel(
                                  isDark: isDark,
                                  color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                ),
                              ),
                            ),
                          ),
                          ...library.songs.take(10).map(
                                (song) => SongTile(
                                  song: song,
                                  playlist: library.songs,
                                ),
                              ),
                          const SizedBox(height: 24),
                        ],
                      ],
                    ),
                  ),
                ),
                const MiniPlayer(),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCategoryGrid(BuildContext context, bool isDark, LibraryProvider library) {
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: _categoryTile(
                context,
                isDark,
                title: 'ALL SONGS',
                count: '${library.songs.length}',
                icon: Icons.music_note,
                onTap: () {
                  Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const AllSongsScreen()),
                  );
                },
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _categoryTile(
                context,
                isDark,
                title: 'ALBUMS',
                count: '${library.albums.length}',
                icon: Icons.album,
                onTap: () {
                  Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const AlbumsScreen()),
                  );
                },
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _categoryTile(
                context,
                isDark,
                title: 'ARTISTS',
                count: '${library.artists.length}',
                icon: Icons.person_outline,
                onTap: () {
                  Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const ArtistsScreen()),
                  );
                },
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _categoryTile(
                context,
                isDark,
                title: 'FOLDERS',
                count: '${library.folders.length}',
                icon: Icons.folder_open,
                onTap: () {
                  Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const FoldersScreen()),
                  );
                },
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _categoryTile(
    BuildContext context,
    bool isDark, {
    required String title,
    required String count,
    required IconData icon,
    required VoidCallback onTap,
  }) {
    return NothingCard(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      onTap: onTap,
      child: Row(
        children: [
          Icon(
            icon,
            size: 20,
            color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    fontFamily: NothingTypography.fontMono,
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 1.0,
                    color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  count,
                  style: NothingTypography.dottedBold(
                    isDark: isDark,
                    fontSize: 20,
                    letterSpacing: 0.8,
                    color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                  ),
                ),
              ],
            ),
          ),
          Icon(
            Icons.chevron_right,
            size: 16,
            color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
          ),
        ],
      ),
    );
  }

  Widget _buildRecentCard(
    BuildContext context,
    bool isDark,
    dynamic song,
    List<dynamic> list,
  ) {
    return GestureDetector(
      onTap: () => context.read<PlayerProvider>().playSong(song, list.cast()),
      child: Container(
        width: 108,
        decoration: BoxDecoration(
          color: isDark ? NothingColors.darkRaised : NothingColors.lightRaised,
          borderRadius: BorderRadius.circular(NothingColors.radiusSm),
          border: Border.all(
            color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
            width: 1,
          ),
        ),
        padding: const EdgeInsets.all(8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            TrackArtwork(
              songId: song.id,
              title: song.title,
              size: 92,
              radius: 4,
            ),
            const SizedBox(height: 8),
            Text(
              song.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontFamily: NothingTypography.fontUi,
                fontSize: 11,
                fontWeight: FontWeight.w600,
                color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              song.artist,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontFamily: NothingTypography.fontUi,
                fontSize: 9,
                color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionBanner(BuildContext context, bool isDark, LibraryProvider library) {
    final isPermDenied = library.permissionStatus == AudioPermissionStatus.permanentlyDenied;

    return Padding(
      padding: const EdgeInsets.only(bottom: 16.0),
      child: NothingCard(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: const BoxDecoration(
                    color: NothingColors.darkAccent,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  'AUDIO STORAGE PERMISSION REQUIRED',
                  style: NothingTypography.monoLabel(
                    isDark: isDark,
                    color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              'Lyro is an offline music player that reads and decodes audio files stored on your device. Please grant audio permission to view your music library.',
              style: NothingTypography.bodyMedium(isDark: isDark),
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                NothingButton(
                  text: isPermDenied ? 'OPEN SETTINGS' : 'GRANT ACCESS',
                  variant: NothingButtonVariant.primary,
                  onPressed: isPermDenied
                      ? () => library.openAppSettings()
                      : () => library.requestPermissionAndLoad(),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context, bool isDark, LibraryProvider library) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 40.0),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.music_off_outlined,
              size: 54,
              color: isDark ? NothingColors.darkLine2 : NothingColors.lightLine2,
            ),
            const SizedBox(height: 16),
            Text(
              'NO AUDIO TRACKS FOUND',
              style: NothingTypography.monoLabel(isDark: isDark),
            ),
            const SizedBox(height: 6),
            Text(
              'Copy FLAC, MP3, WAV or AAC files to your device storage.',
              textAlign: TextAlign.center,
              style: NothingTypography.bodySmall(isDark: isDark),
            ),
            const SizedBox(height: 20),
            NothingButton(
              text: 'RESCAN STORAGE',
              variant: NothingButtonVariant.secondary,
              onPressed: () => library.loadLibrary(isRescan: true),
            ),
          ],
        ),
      ),
    );
  }
}
