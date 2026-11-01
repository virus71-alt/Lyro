class AppConstants {
  AppConstants._();

  static const String appName = 'Lyro';
  static const String appTagline = 'Pure sound. Minimal form.';
  static const String appVersion = '1.0.0 (MVP Phase 1)';
  static const String bundleId = 'com.lyro.doyalabs';

  // Supported audio formats
  static const List<String> supportedExtensions = [
    'flac',
    'mp3',
    'wav',
    'm4a',
    'aac',
    'ogg',
    'opus',
  ];

  // Preference keys
  static const String prefThemeMode = 'lyro_theme_mode';
  static const String prefSortOrder = 'lyro_song_sort_order';
  static const String prefShuffle = 'lyro_shuffle_mode';
  static const String prefRepeat = 'lyro_repeat_mode';
  static const String prefFavorites = 'lyro_favorites';
  static const String prefRecentlyPlayed = 'lyro_recently_played';
}

