import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:on_audio_query/on_audio_query.dart';
import '../../core/constants/app_constants.dart';
import '../../core/utils/formatters.dart';
import '../models/album_model.dart';
import '../models/artist_model.dart';
import '../models/folder_model.dart';
import '../models/song_model.dart';

enum SongSort {
  titleAsc,
  titleDesc,
  artistAsc,
  albumAsc,
  durationDesc,
  dateAddedDesc,
}

class MusicRepository {
  final OnAudioQuery _audioQuery;

  MusicRepository({OnAudioQuery? audioQuery}) : _audioQuery = audioQuery ?? OnAudioQuery();

  OnAudioQuery get audioQuery => _audioQuery;

  /// Queries all local songs from the device media store.
  Future<List<Song>> fetchAllSongs({SongSort sort = SongSort.titleAsc}) async {
    try {
      final List<SongModel> rawSongs = await _audioQuery.querySongs(
        sortType: null, // We sort in Dart for consistent cross-platform behavior
        orderType: OrderType.ASC_OR_SMALLER,
        uriType: UriType.EXTERNAL,
        ignoreCase: true,
      );

      final List<Song> songs = [];
      for (final raw in rawSongs) {
        // Exclude ringtones / podcasts / notifications or very short files (< 3s)
        if (raw.isMusic == false && raw.isMusic != null) continue;
        if (raw.duration != null && raw.duration! < 3000) continue;

        final path = raw.data;
        final ext = raw.fileExtension.toLowerCase();

        // Check if supported format
        final format = Formatters.formatCodec(ext, path);
        final isSupported = AppConstants.supportedExtensions.contains(ext) ||
            AppConstants.supportedExtensions.contains(format.toLowerCase());

        // Even if extension is blank (some iOS tracks), if duration > 0 include it
        if (!isSupported && ext.isNotEmpty) continue;

        songs.add(
          Song(
            id: raw.id,
            title: raw.title.trim().isNotEmpty ? raw.title.trim() : 'Track ${raw.id}',
            artist: (raw.artist != null &&
                    raw.artist!.trim().isNotEmpty &&
                    raw.artist != '<unknown>')
                ? raw.artist!.trim()
                : 'Unknown Artist',
            album: (raw.album != null &&
                    raw.album!.trim().isNotEmpty &&
                    raw.album != '<unknown>')
                ? raw.album!.trim()
                : 'Unknown Album',
            albumId: raw.albumId,
            duration: raw.duration ?? 0,
            path: raw.data,
            uri: raw.uri,
            format: format,
            trackNumber: raw.track,
            size: raw.size,
            dateAdded: raw.dateAdded,
          ),
        );
      }

      return _applySort(songs, sort);
    } catch (e) {
      debugPrint('Error querying songs: $e');
      return [];
    }
  }

  /// Queries all albums
  Future<List<Album>> fetchAllAlbums() async {
    try {
      final List<AlbumModel> rawAlbums = await _audioQuery.queryAlbums(
        sortType: AlbumSortType.ALBUM,
        orderType: OrderType.ASC_OR_SMALLER,
        uriType: UriType.EXTERNAL,
        ignoreCase: true,
      );

      return rawAlbums
          .map((a) => Album(
                id: a.id,
                album: a.album.trim().isNotEmpty ? a.album.trim() : 'Unknown Album',
                artist: (a.artist != null && a.artist!.trim().isNotEmpty)
                    ? a.artist!.trim()
                    : 'Unknown Artist',
                numOfSongs: a.numOfSongs,
              ))
          .where((a) => a.numOfSongs > 0)
          .toList();
    } catch (e) {
      debugPrint('Error querying albums: $e');
      return [];
    }
  }

  /// Queries all artists
  Future<List<Artist>> fetchAllArtists() async {
    try {
      final List<ArtistModel> rawArtists = await _audioQuery.queryArtists(
        sortType: ArtistSortType.ARTIST,
        orderType: OrderType.ASC_OR_SMALLER,
        uriType: UriType.EXTERNAL,
        ignoreCase: true,
      );

      return rawArtists
          .map((a) => Artist(
                id: a.id,
                artist: a.artist.trim().isNotEmpty ? a.artist.trim() : 'Unknown Artist',
                numberOfAlbums: a.numberOfAlbums ?? 0,
                numberOfTracks: a.numberOfTracks ?? 0,
              ))
          .where((a) => a.numberOfTracks > 0)
          .toList();
    } catch (e) {
      debugPrint('Error querying artists: $e');
      return [];
    }
  }

  /// Groups songs into folders based on their parent directories
  List<MusicFolder> groupSongsByFolder(List<Song> songs) {
    final Map<String, List<Song>> folderMap = {};

    for (final song in songs) {
      if (song.path.isEmpty) continue;
      final file = File(song.path);
      final parentDir = file.parent.path;
      folderMap.putIfAbsent(parentDir, () => []).add(song);
    }

    final List<MusicFolder> folders = [];
    folderMap.forEach((path, folderSongs) {
      final name = path.split(Platform.pathSeparator).where((s) => s.isNotEmpty).lastOrNull ?? path;
      folders.add(MusicFolder(
        path: path,
        name: name,
        songs: folderSongs,
      ));
    });

    folders.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
    return folders;
  }

  List<Song> _applySort(List<Song> songs, SongSort sort) {
    final sorted = List<Song>.from(songs);
    switch (sort) {
      case SongSort.titleAsc:
        sorted.sort((a, b) => a.title.toLowerCase().compareTo(b.title.toLowerCase()));
        break;
      case SongSort.titleDesc:
        sorted.sort((a, b) => b.title.toLowerCase().compareTo(a.title.toLowerCase()));
        break;
      case SongSort.artistAsc:
        sorted.sort((a, b) => a.artist.toLowerCase().compareTo(b.artist.toLowerCase()));
        break;
      case SongSort.albumAsc:
        sorted.sort((a, b) => a.album.toLowerCase().compareTo(b.album.toLowerCase()));
        break;
      case SongSort.durationDesc:
        sorted.sort((a, b) => b.duration.compareTo(a.duration));
        break;
      case SongSort.dateAddedDesc:
        sorted.sort((a, b) => (b.dateAdded ?? 0).compareTo(a.dateAdded ?? 0));
        break;
    }
    return sorted;
  }
}
