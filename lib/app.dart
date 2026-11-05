import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'core/constants/app_constants.dart';
import 'core/theme/nothing_theme.dart';
import 'core/theme/theme_provider.dart';
import 'data/repositories/music_repository.dart';
import 'data/repositories/preferences_repository.dart';
import 'data/services/audio_handler.dart';
import 'features/library/providers/library_provider.dart';
import 'features/navigation/screens/main_navigation_screen.dart';
import 'features/onboarding/onboarding_screen.dart';
import 'features/navigation/widgets/widget_action_listener.dart';
import 'features/player/providers/player_provider.dart';
import 'features/profile/providers/profile_provider.dart';
import 'core/settings/app_settings_provider.dart';
import 'features/playlists/providers/playlist_provider.dart';
import 'features/eq/eq_provider.dart';
import 'features/player/widgets/cmf_speaker_3d.dart';
import 'data/services/web_stream_server.dart';

class LyroApp extends StatelessWidget {
  final PreferencesRepository prefsRepo;
  final MusicRepository musicRepo;
  final LyroAudioHandler audioHandler;

  const LyroApp({
    super.key,
    required this.prefsRepo,
    required this.musicRepo,
    required this.audioHandler,
  });

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(
          create: (_) => ThemeProvider(prefsRepo),
        ),
        ChangeNotifierProvider(
          create: (_) => ProfileProvider(prefsRepo),
        ),
        ChangeNotifierProvider(
          create: (_) => AppSettingsProvider(prefsRepo: prefsRepo, audioHandler: audioHandler),
        ),
        ChangeNotifierProvider(
          create: (_) => PlaylistProvider(prefsRepo),
        ),
        ChangeNotifierProvider(
          lazy: false, // apply the saved EQ as soon as playback starts
          create: (_) => EqProvider(prefsRepo: prefsRepo, audioHandler: audioHandler),
        ),
        ChangeNotifierProvider(
          create: (_) => LibraryProvider(
            musicRepo: musicRepo,
            prefsRepo: prefsRepo,
          ),
        ),
        ChangeNotifierProvider(
          create: (context) => PlayerProvider(
            audioHandler: audioHandler,
            prefsRepo: prefsRepo,
          )..librarySongs = () => context.read<LibraryProvider>().songs,
        ),
        ChangeNotifierProvider(
          create: (context) => WebStreamServer(prefsRepo: prefsRepo)
            ..songs = (() => context.read<LibraryProvider>().songs)
            ..toggleFavorite = ((id) => context.read<PlayerProvider>().toggleFavorite(id))
            ..speakerLook = (() {
              final settings = context.read<AppSettingsProvider>();
              final look = settings.colorway(Brightness.dark);
              return {
                'style': SpeakerShape.values.indexOf(settings.speakerShape),
                'body': speakerColorHex(look.body),
                'knob': speakerColorHex(look.knob),
                'dial': speakerColorHex(look.dial),
              };
            })
            ..playlists = (() => [
                  for (final p in context.read<PlaylistProvider>().playlists)
                    (name: p.name, songs: p.songIds),
                ]),
        ),
      ],
      child: Consumer<ThemeProvider>(
        builder: (context, themeProvider, _) {
          return MaterialApp(
            title: AppConstants.appName,
            debugShowCheckedModeBanner: false,
            theme: NothingTheme.lightTheme,
            darkTheme: NothingTheme.darkTheme,
            themeMode: themeProvider.themeMode,
            // First launch explains permissions before Android asks;
            // anyone who already granted music access goes straight in.
            home: Builder(
              builder: (context) {
                final onboarded = context.select<AppSettingsProvider, bool>((s) => s.onboarded);
                final granted = context.select<LibraryProvider, bool>((l) => l.isPermissionGranted);
                final checked = context.select<LibraryProvider, bool>((l) => l.permissionChecked);
                if (onboarded || granted) {
                  // Quick-mix taps from the home-screen widget start playback
                  return const WidgetActionListener(child: MainNavigationScreen());
                }
                if (!checked) return const ColoredBox(color: Colors.black);
                return const OnboardingScreen();
              },
            ),
          );
        },
      ),
    );
  }
}

