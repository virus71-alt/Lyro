import 'dart:convert';

import 'package:flutter/foundation.dart';

import '../../../data/models/song_model.dart';
import '../../../data/repositories/preferences_repository.dart';

class Playlist {
  final String id;
  final String name;
  final List<int> songIds;
  final int created;

  const Playlist({
    required this.id,
    required this.name,
    required this.songIds,
    required this.created,
  });

  Playlist copyWith({String? name, List<int>? songIds}) => Playlist(
        id: id,
        name: name ?? this.name,
        songIds: songIds ?? this.songIds,
        created: created,
      );

  Map<String, dynamic> toJson() =>
      {'id': id, 'name': name, 'songs': songIds, 'created': created};

  static Playlist fromJson(Map<String, dynamic> j) => Playlist(
        id: j['id'] as String,
        name: j['name'] as String,
        songIds: [for (final s in j['songs'] as List) s as int],
        created: j['created'] as int? ?? 0,
      );
}

/// User playlists, stored as JSON in preferences (by song id).
class PlaylistProvider extends ChangeNotifier {
  final PreferencesRepository prefsRepo;
  List<Playlist> _playlists = [];

  PlaylistProvider(this.prefsRepo) {
    final raw = prefsRepo.getPlaylistsJson();
    if (raw != null) {
      try {
        _playlists = [
          for (final j in jsonDecode(raw) as List) Playlist.fromJson(j as Map<String, dynamic>),
        ];
      } catch (_) {
        _playlists = [];
      }
    }
  }

  List<Playlist> get playlists => List.unmodifiable(_playlists);

  Playlist? byId(String id) => _playlists.where((p) => p.id == id).firstOrNull;

  /// Resolves a playlist's songs against the library (missing files skipped).
  List<Song> songsOf(Playlist p, List<Song> library) {
    final byId = {for (final s in library) s.id: s};
    return [
      for (final id in p.songIds)
        if (byId[id] != null) byId[id]!,
    ];
  }

  Future<Playlist> create(String name, {List<Song> songs = const []}) async {
    final p = Playlist(
      id: DateTime.now().microsecondsSinceEpoch.toRadixString(36),
      name: name.trim().isEmpty ? 'New playlist' : name.trim(),
      songIds: [for (final s in songs) s.id],
      created: DateTime.now().millisecondsSinceEpoch,
    );
    _playlists = [..._playlists, p];
    await _save();
    return p;
  }

  /// Adds [songs] (skipping ones already in it); returns how many were added.
  Future<int> addSongs(String id, List<Song> songs) async {
    final p = byId(id);
    if (p == null) return 0;
    final ids = [...p.songIds];
    var added = 0;
    for (final s in songs) {
      if (!ids.contains(s.id)) {
        ids.add(s.id);
        added++;
      }
    }
    await _replace(p.copyWith(songIds: ids));
    return added;
  }

  Future<void> removeSongAt(String id, int index) async {
    final p = byId(id);
    if (p == null || index < 0 || index >= p.songIds.length) return;
    await _replace(p.copyWith(songIds: [...p.songIds]..removeAt(index)));
  }

  Future<void> moveSong(String id, int oldIndex, int newIndex) async {
    final p = byId(id);
    if (p == null) return;
    final ids = [...p.songIds];
    final item = ids.removeAt(oldIndex);
    ids.insert(newIndex.clamp(0, ids.length), item);
    await _replace(p.copyWith(songIds: ids));
  }

  /// Replaces the song order outright (e.g. undoing a removal).
  Future<void> restoreIds(String id, List<int> ids) async {
    final p = byId(id);
    if (p == null) return;
    await _replace(p.copyWith(songIds: ids));
  }

  Future<void> rename(String id, String name) async {
    final p = byId(id);
    if (p == null || name.trim().isEmpty) return;
    await _replace(p.copyWith(name: name.trim()));
  }

  Future<void> delete(String id) async {
    _playlists = _playlists.where((p) => p.id != id).toList();
    await _save();
  }

  Future<void> _replace(Playlist p) async {
    _playlists = [for (final x in _playlists) x.id == p.id ? p : x];
    await _save();
  }

  Future<void> _save() async {
    notifyListeners();
    await prefsRepo.setPlaylistsJson(jsonEncode([for (final p in _playlists) p.toJson()]));
  }
}
