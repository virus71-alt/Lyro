import 'dart:async';
import 'dart:io';

import 'package:audio_service/audio_service.dart';
import 'package:flutter/services.dart';
import 'package:on_audio_query/on_audio_query.dart';
import 'package:path_provider/path_provider.dart';

import '../../features/player/widgets/artwork_cache.dart';
import 'audio_handler.dart';

/// Keeps the Android home-screen widget in sync with playback: title,
/// artist, play state, progress (every 15 s while playing) and cover art.
class HomeWidgetBridge {
  HomeWidgetBridge._();

  static const _channel = MethodChannel('lyro/widget');
  static const _progressEvery = Duration(seconds: 15);

  static MediaItem? _item;
  static bool _playing = false;
  static String? _artPath;
  static Timer? _ticker;
  static LyroAudioHandler? _handler;

  static void attach(LyroAudioHandler handler) {
    if (!Platform.isAndroid) return;
    _handler = handler;
    handler.mediaItem.listen((item) async {
      if (item?.id == _item?.id) return;
      _item = item;
      _artPath = await _writeArt(item);
      _push();
    });
    handler.playbackState.listen((state) {
      if (state.playing == _playing) return;
      _playing = state.playing;
      _ticker?.cancel();
      if (_playing) _ticker = Timer.periodic(_progressEvery, (_) => _push());
      _push();
    });
  }

  static Future<String?> _writeArt(MediaItem? item) async {
    final id = item?.extras?['songId'] as int?;
    if (id == null || id <= 0) return null;
    try {
      final bytes = await ArtworkCache.load(id, ArtworkType.AUDIO, 360);
      if (bytes == null) return null;
      final dir = await getApplicationSupportDirectory();
      final file = File('${dir.path}/widget_art');
      await file.writeAsBytes(bytes, flush: true);
      return file.path;
    } catch (_) {
      return null;
    }
  }

  static Future<void> _push() async {
    final item = _item;
    final handler = _handler;
    final duration = item?.duration?.inMilliseconds ?? 0;
    final position = handler?.player.position.inMilliseconds ?? 0;
    try {
      await _channel.invokeMethod('update', {
        'hasSong': item != null,
        'title': item?.title ?? '',
        'artist': item?.artist ?? '',
        'playing': _playing,
        'progress': duration > 0 ? (position / duration).clamp(0.0, 1.0) : 0.0,
        'artPath': _artPath,
      });
    } catch (_) {
      // Widget channel unavailable (tests, other platforms)
    }
  }
}
