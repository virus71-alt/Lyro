import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'app.dart';
import 'data/repositories/music_repository.dart';
import 'data/repositories/preferences_repository.dart';
import 'data/services/playback_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Lock to portrait orientation for optimal Nothing OS aesthetic
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  // Set system UI style
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: Colors.transparent,
    ),
  );

  // Initialize persistent repositories and background audio service
  final prefsRepo = await PreferencesRepository.init();
  final audioHandler = await PlaybackService.init();
  final musicRepo = MusicRepository();

  runApp(
    LyroApp(
      prefsRepo: prefsRepo,
      musicRepo: musicRepo,
      audioHandler: audioHandler,
    ),
  );
}
