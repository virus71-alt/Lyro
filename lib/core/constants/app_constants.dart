class AppConstants {
  AppConstants._();

  static const String appName = 'Lyro';
  static const String appTagline = 'Pure sound. Minimal form.';
  static const String appVersion = '1.0.0';
  static const String bundleId = 'com.lyro.doyalabs';
  static const String privacyPolicyUrl = 'https://github.com/virus71-alt/Lyro/blob/main/PRIVACY.md';

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
  static const String prefProfileName = 'lyro_profile_name';
  static const String prefProfileAvatar = 'lyro_profile_avatar';
  static const String prefOnlineArtwork = 'lyro_online_artwork';
  static const String prefPlayStats = 'lyro_play_stats';
  static const String prefPlaylists = 'lyro_playlists';
  static const String prefReduceMotion = 'lyro_reduce_motion';
  static const String prefSpeakerColor = 'lyro_speaker_color';
  static const String prefSpeakerStyle = 'lyro_speaker_style';
  static const String prefFadeSeconds = 'lyro_fade_seconds';
  static const String prefOnboarded = 'lyro_onboarded';
  static const String prefEqualizer = 'lyro_equalizer';
  static const String prefWebStreamKey = 'lyro_web_stream_key';
}

