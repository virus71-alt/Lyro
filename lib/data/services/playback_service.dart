import 'package:audio_service/audio_service.dart';
import 'package:flutter/material.dart';
import 'audio_handler.dart';

class PlaybackService {
  static LyroAudioHandler? _handler;

  static LyroAudioHandler get handler {
    if (_handler == null) {
      throw StateError('PlaybackService has not been initialized. Call init() first.');
    }
    return _handler!;
  }

  static Future<LyroAudioHandler> init() async {
    if (_handler != null) return _handler!;

    _handler = await AudioService.init(
      builder: () => LyroAudioHandler(),
      config: const AudioServiceConfig(
        androidNotificationChannelId: 'com.lyro.doyalabs.audio',
        androidNotificationChannelName: 'Lyro Audio Playback',
        androidNotificationChannelDescription: 'Background audio playback controls for Lyro',
        androidNotificationOngoing: true,
        androidStopForegroundOnPause: true,
        androidShowNotificationBadge: true,
        notificationColor: Color(0xFF171717),
      ),
    );

    debugPrint('PlaybackService & LyroAudioHandler initialized successfully');
    return _handler!;
  }
}
