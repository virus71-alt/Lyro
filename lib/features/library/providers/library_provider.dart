import 'package:flutter/foundation.dart';
import '../../../core/permissions/permission_service.dart';
import '../../../data/models/album_model.dart';
import '../../../data/models/artist_model.dart';
import '../../../data/models/folder_model.dart';
import '../../../data/models/song_model.dart';
import '../../../data/repositories/music_repository.dart';
import '../../../data/repositories/preferences_repository.dart';

class LibraryProvider extends ChangeNotifier {
  final MusicRepository musicRepo;
  final PreferencesRepository prefsRepo;

  bool _isLoading = true;
  String? _errorMessage;
  AudioPermissionStatus _permissionStatus = AudioPermissionStatus.denied;

  List<Song> _songs = [];
  List<Album> _albums = [];
  List<Artist> _artists = [];
  List<MusicFolder> _folders = [];
  SongSort _currentSort = SongSort.titleAsc;

  LibraryProvider({
    required this.musicRepo,
    required this.prefsRepo,
  }) {
    init();
  }

  // Getters
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  AudioPermissionStatus get permissionStatus => _permissionStatus;
  bool get isPermissionGranted => _permissionStatus == AudioPermissionStatus.granted;

  List<Song> get songs => List.unmodifiable(_songs);
  List<Album> get albums => List.unmodifiable(_albums);
  List<Artist> get artists => List.unmodifiable(_artists);
  List<MusicFolder> get folders => List.unmodifiable(_folders);
  SongSort get currentSort => _currentSort;

  List<Song> get recentlyPlayedSongs {
    final recentIds = prefsRepo.getRecentlyPlayed();
    final Map<int, Song> songMap = {for (var s in _songs) s.id: s};
    return recentIds.map((id) => songMap[id]).whereType<Song>().toList();
  }

  List<Song> get favoriteSongs {
    final favIds = prefsRepo.getFavorites();
    return _songs.where((s) => favIds.contains(s.id)).toList();
  }

  Future<void> init() async {
    _permissionStatus = await PermissionService.instance.checkAudioPermission();
    if (_permissionStatus == AudioPermissionStatus.granted) {
      await loadLibrary();
    } else {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> requestPermissionAndLoad() async {
    _isLoading = true;
    notifyListeners();

    _permissionStatus = await PermissionService.instance.requestAudioPermission();
    if (_permissionStatus == AudioPermissionStatus.granted) {
      await loadLibrary();
    } else {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> openAppSettings() async {
    await PermissionService.instance.openSettings();
  }

  Future<void> loadLibrary({bool isRescan = false}) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final fetchedSongs = await musicRepo.fetchAllSongs(sort: _currentSort);
      final fetchedAlbums = await musicRepo.fetchAllAlbums();
      final fetchedArtists = await musicRepo.fetchAllArtists();
      final groupedFolders = musicRepo.groupSongsByFolder(fetchedSongs);

      // Build high-precision maps to link every album to a valid song ID so album
      // artwork queries load the track's real embedded thumbnail reliably on all Android versions
      final Map<int, int> albumIdToSongId = {};
      final Map<String, int> albumNameToSongId = {};
      final Map<String, int> songTitleToSongId = {};
      final Map<String, int> artistNameToSongId = {};

      for (final song in fetchedSongs) {
        if (song.albumId != null && !albumIdToSongId.containsKey(song.albumId)) {
          albumIdToSongId[song.albumId!] = song.id;
        }
        final lowerAlbum = song.album.toLowerCase().trim();
        if (lowerAlbum.isNotEmpty && lowerAlbum != 'unknown album' && !albumNameToSongId.containsKey(lowerAlbum)) {
          albumNameToSongId[lowerAlbum] = song.id;
        }
        final lowerTitle = song.title.toLowerCase().trim();
        if (lowerTitle.isNotEmpty && !songTitleToSongId.containsKey(lowerTitle)) {
          songTitleToSongId[lowerTitle] = song.id;
        }
        final lowerArtist = song.artist.toLowerCase().trim();
        if (lowerArtist.isNotEmpty && lowerArtist != 'unknown artist' && !artistNameToSongId.containsKey(lowerArtist)) {
          artistNameToSongId[lowerArtist] = song.id;
        }
      }

      final mappedAlbums = fetchedAlbums.map((a) {
        final songId = _resolveSongIdForAlbum(a, fetchedSongs);
        return a.copyWith(songId: songId);
      }).toList();

      _songs = fetchedSongs;
      _albums = mappedAlbums;
      _artists = fetchedArtists;
      _folders = groupedFolders;
    } catch (e) {
      _errorMessage = 'Unable to scan music library. Please try again.';
      debugPrint('Library scan error: $e');
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// High-precision resolver that links an Album to a playable Song ID so that
  /// album artwork queries load the track's embedded thumbnail on all Android versions.
  static int? _resolveSongIdForAlbum(Album album, List<Song> songs) {
    if (album.songId != null && album.songId! > 0) return album.songId;

    // 1. Direct albumId match
    final byId = songs.where((s) => s.albumId == album.id).firstOrNull;
    if (byId != null) return byId.id;

    final normAlbum = album.album.replaceAll(RegExp(r'[^\w\s]'), '').toLowerCase().trim();
    final normArtist = album.artist.replaceAll(RegExp(r'[^\w\s]'), '').toLowerCase().trim();

    // 2. Exact or normalized album name match
    if (normAlbum.isNotEmpty && normAlbum != 'unknown album') {
      final byName = songs.where((s) {
        final sAlbum = s.album.replaceAll(RegExp(r'[^\w\s]'), '').toLowerCase().trim();
        return sAlbum == normAlbum || sAlbum.contains(normAlbum) || normAlbum.contains(sAlbum);
      }).firstOrNull;
      if (byName != null) return byName.id;
    }

    // 3. Artist match
    if (normArtist.isNotEmpty && normArtist != 'unknown artist') {
      final byArtist = songs.where((s) {
        final sArtist = s.artist.replaceAll(RegExp(r'[^\w\s]'), '').toLowerCase().trim();
        return sArtist == normArtist || sArtist.contains(normArtist) || normArtist.contains(sArtist);
      }).firstOrNull;
      if (byArtist != null) return byArtist.id;
    }

    // 4. Song title match (singles, YouTube downloads often have album = title)
    if (normAlbum.isNotEmpty && normAlbum != 'unknown album') {
      final byTitle = songs.where((s) {
        final sTitle = s.title.replaceAll(RegExp(r'[^\w\s]'), '').toLowerCase().trim();
        return sTitle == normAlbum || sTitle.contains(normAlbum) || normAlbum.contains(sTitle);
      }).firstOrNull;
      if (byTitle != null) return byTitle.id;
    }

    // 5. Fallback: match any song if album has songs
    return songs.isNotEmpty ? songs.first.id : null;
  }

  void setSort(SongSort sort) {
    if (_currentSort == sort) return;
    _currentSort = sort;
    _songs = _applySort(_songs, _currentSort);
    notifyListeners();
  }

  int? getArtworkSongIdForAlbum(int albumId, [String? albumName, String? artistName]) {
    final album = _albums.where((a) => a.id == albumId).firstOrNull;
    if (album?.songId != null && album!.songId! > 0) return album.songId;

    if (album != null) {
      return _resolveSongIdForAlbum(album, _songs);
    }
    return _resolveSongIdForAlbum(
      Album(id: albumId, album: albumName ?? '', artist: artistName ?? '', numOfSongs: 0),
      _songs,
    );
  }

  List<Song> getSongsForAlbum(int albumId, [String? albumName, String? artistName]) {
    final album = _albums.where((a) => a.id == albumId).firstOrNull;
    final targetAlbum = (albumName ?? album?.album ?? '').toLowerCase().trim();
    final targetArtist = (artistName ?? album?.artist ?? '').toLowerCase().trim();

    final byId = _songs.where((s) => s.albumId == albumId).toList();
    if (byId.isNotEmpty) {
      return byId..sort((a, b) => (a.trackNumber ?? 0).compareTo(b.trackNumber ?? 0));
    }

    if (targetAlbum.isNotEmpty && targetAlbum != 'unknown album') {
      final byName = _songs.where((s) => s.album.toLowerCase().trim() == targetAlbum).toList();
      if (byName.isNotEmpty) {
        return byName..sort((a, b) => (a.trackNumber ?? 0).compareTo(b.trackNumber ?? 0));
      }

      final byTitle = _songs.where((s) => s.title.toLowerCase().trim() == targetAlbum).toList();
      if (byTitle.isNotEmpty) {
        return byTitle..sort((a, b) => (a.trackNumber ?? 0).compareTo(b.trackNumber ?? 0));
      }
    }

    if (targetArtist.isNotEmpty && targetArtist != 'unknown artist') {
      final byArtist = _songs.where((s) => s.artist.toLowerCase().trim() == targetArtist).toList();
      if (byArtist.isNotEmpty) {
        return byArtist..sort((a, b) => (a.trackNumber ?? 0).compareTo(b.trackNumber ?? 0));
      }
    }

    return [];
  }

  List<Song> getSongsForArtist(String artistName) {
    final lower = artistName.toLowerCase().trim();
    return _songs.where((s) => s.artist.toLowerCase().trim() == lower).toList();
  }

  List<Song> getSongsForFolder(String folderPath) {
    final found = _folders.where((f) => f.path == folderPath).firstOrNull;
    return found?.songs ?? [];
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