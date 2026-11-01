import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'core/constants/app_constants.dart';
import 'core/theme/nothing_theme.dart';
import 'core/theme/theme_provider.dart';
import 'data/repositories/music_repository.dart';
import 'data/repositories/preferences_repository.dart';
import 'data/services/audio_handler.dart';
import 'features/library/providers/library_provider.dart';
import 'features/library/screens/home_screen.dart';
import 'features/player/providers/player_provider.dart';

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
          create: (_) => LibraryProvider(
            musicRepo: musicRepo,
            prefsRepo: prefsRepo,
          ),
        ),
        ChangeNotifierProvider(
          create: (_) => PlayerProvider(
            audioHandler: audioHandler,
            prefsRepo: prefsRepo,
          ),
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
            home: const HomeScreen(),
          );
        },
      ),
    );
  }
}

