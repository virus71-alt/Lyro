import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../core/constants/app_constants.dart';

class PreferencesRepository {
  final SharedPreferences _prefs;

  PreferencesRepository(this._prefs);

  static Future<PreferencesRepository> init() async {
    final prefs = await SharedPreferences.getInstance();
    return PreferencesRepository(prefs);
  }

  // Theme Mode
  ThemeMode getThemeMode() {
    final val = _prefs.getString(AppConstants.prefThemeMode);
    if (val == 'light') return ThemeMode.light;
    if (val == 'dark') return ThemeMode.dark;
    return ThemeMode.system;
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    switch (mode) {
      case ThemeMode.light:
        await _prefs.setString(AppConstants.prefThemeMode, 'light');
        break;
      case ThemeMode.dark:
        await _prefs.setString(AppConstants.prefThemeMode, 'dark');
        break;
      case ThemeMode.system:
        await _prefs.setString(AppConstants.prefThemeMode, 'system');
        break;
    }
  }

  // Favorites (Song IDs)
  Set<int> getFavorites() {
    final list = _prefs.getStringList(AppConstants.prefFavorites) ?? [];
    return list.map((id) => int.tryParse(id)).whereType<int>().toSet();
  }

  Future<void> toggleFavorite(int songId) async {
    final favorites = getFavorites();
    if (favorites.contains(songId)) {
      favorites.remove(songId);
    } else {
      favorites.add(songId);
    }
    await _prefs.setStringList(
      AppConstants.prefFavorites,
      favorites.map((e) => e.toString()).toList(),
    );
  }

  // Recently Played (List of Song IDs, max 50)
  List<int> getRecentlyPlayed() {
    final list = _prefs.getStringList(AppConstants.prefRecentlyPlayed) ?? [];
    return list.map((id) => int.tryParse(id)).whereType<int>().toList();
  }

  Future<void> addRecentlyPlayed(int songId) async {
    final list = getRecentlyPlayed();
    list.remove(songId);
    list.insert(0, songId);
    if (list.length > 50) {
      list.removeRange(50, list.length);
    }
    await _prefs.setStringList(
      AppConstants.prefRecentlyPlayed,
      list.map((e) => e.toString()).toList(),
    );
  }

  // Profile
  String getProfileName() =>
      _prefs.getString(AppConstants.prefProfileName) ?? 'Lyro Listener';

  Future<void> setProfileName(String name) =>
      _prefs.setString(AppConstants.prefProfileName, name);

  int getProfileAvatar() => _prefs.getInt(AppConstants.prefProfileAvatar) ?? 0;

  Future<void> setProfileAvatar(int id) =>
      _prefs.setInt(AppConstants.prefProfileAvatar, id);

  // Artwork
  bool getOnlineArtwork() => _prefs.getBool(AppConstants.prefOnlineArtwork) ?? true;

  Future<void> setOnlineArtwork(bool enabled) =>
      _prefs.setBool(AppConstants.prefOnlineArtwork, enabled);

  bool getAskedImagePermission() =>
      _prefs.getBool(AppConstants.prefAskedImagePermission) ?? false;

  Future<void> setAskedImagePermission() =>
      _prefs.setBool(AppConstants.prefAskedImagePermission, true);

  // Play statistics: songId -> (play count, last played ms since epoch)
  Map<int, ({int count, int lastPlayed})> getPlayStats() {
    final raw = _prefs.getString(AppConstants.prefPlayStats);
    if (raw == null) return {};
    try {
      final map = jsonDecode(raw) as Map<String, dynamic>;
      return {
        for (final e in map.entries)
          int.parse(e.key): (
            count: (e.value as List)[0] as int,
            lastPlayed: (e.value as List)[1] as int,
          ),
      };
    } catch (_) {
      return {};
    }
  }

  Future<void> recordPlay(int songId) async {
    final stats = getPlayStats();
    final prev = stats[songId];
    stats[songId] = (
      count: (prev?.count ?? 0) + 1,
      lastPlayed: DateTime.now().millisecondsSinceEpoch,
    );
    await _prefs.setString(
      AppConstants.prefPlayStats,
      jsonEncode({
        for (final e in stats.entries) '${e.key}': [e.value.count, e.value.lastPlayed],
      }),
    );
  }

  // Playlists (JSON list of {id, name, songs: [ids], created})
  String? getPlaylistsJson() => _prefs.getString(AppConstants.prefPlaylists);

  Future<void> setPlaylistsJson(String json) =>
      _prefs.setString(AppConstants.prefPlaylists, json);

  // App settings
  bool getReduceMotion() => _prefs.getBool(AppConstants.prefReduceMotion) ?? false;
  Future<void> setReduceMotion(bool v) => _prefs.setBool(AppConstants.prefReduceMotion, v);

  int getSpeakerColor() => _prefs.getInt(AppConstants.prefSpeakerColor) ?? 0;
  Future<void> setSpeakerColor(int v) => _prefs.setInt(AppConstants.prefSpeakerColor, v);

  int getFadeSeconds() => _prefs.getInt(AppConstants.prefFadeSeconds) ?? 0;
  Future<void> setFadeSeconds(int v) => _prefs.setInt(AppConstants.prefFadeSeconds, v);

  bool getOnboarded() => _prefs.getBool(AppConstants.prefOnboarded) ?? false;
  Future<void> setOnboarded() => _prefs.setBool(AppConstants.prefOnboarded, true);

  // Equalizer state (JSON: enabled, selected profile, fine-tune, downloads)
  String? getEqualizerJson() => _prefs.getString(AppConstants.prefEqualizer);
  Future<void> setEqualizerJson(String json) => _prefs.setString(AppConstants.prefEqualizer, json);
}
