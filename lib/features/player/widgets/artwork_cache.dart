import 'dart:async';
import 'dart:collection';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:on_audio_query/on_audio_query.dart';

import '../../../data/models/song_model.dart';
import '../../../data/services/embedded_artwork.dart';
import '../../../data/services/folder_artwork.dart';
import '../../../data/services/online_artwork.dart';

/// In-memory LRU cache of embedded artwork bytes.
///
/// `QueryArtworkWidget` starts a new platform query (and full image decode)
/// every time it rebuilds; widgets that rebuild per animation frame or while
/// scrolling made that the main source of jank. This resolves each artwork
/// once, shares in-flight requests, and keeps the same [Uint8List] instance
/// so Flutter's image cache also hits.
///
/// Sources, in order: MediaStore's thumbnailer, the file's own tags
/// ([EmbeddedArtwork], for covers MediaStore misses), an album image next to
/// the file ([FolderArtwork]), then — if [onlineEnabled] — Apple's catalogue
/// ([OnlineArtwork]). Misses are retried after [_missRetry], so a transient
/// failure doesn't hide a cover for the whole session.
class ArtworkCache {
  ArtworkCache._();

  static const int _maxEntries = 400;
  static const int _maxConcurrent = 4;
  static const Duration _missRetry = Duration(minutes: 1);

  static final OnAudioQuery _query = OnAudioQuery();
  static final LinkedHashMap<String, Uint8List?> _cache = LinkedHashMap();
  static final Map<String, DateTime> _missedAt = {};
  static final Map<String, Future<Uint8List?>> _pending = {};
  static final Map<int, Song> _songs = {};

  /// Whether missing covers may be looked up online (user setting).
  static bool onlineEnabled = true;

  /// Bumped by [forgetMisses]; blank artwork widgets listen to retry.
  static final ValueNotifier<int> revision = ValueNotifier(0);

  static int _running = 0;
  static final Queue<Completer<void>> _waiting = Queue();

  /// Lets the cache find each song's file and tags for the fallbacks.
  static void registerSongs(Iterable<Song> songs) {
    for (final s in songs) {
      _songs[s.id] = s;
    }
    FolderArtwork.clear();
  }

  /// Retry everything that previously had no cover (e.g. after enabling
  /// online artwork or granting the photos permission).
  static void forgetMisses() {
    _cache.removeWhere((_, bytes) => bytes == null);
    _missedAt.clear();
    FolderArtwork.clear();
    revision.value++;
  }

  /// Requested pixel sizes are bucketed so nearby sizes share one entry.
  static int bucket(double pixels) => pixels <= 160 ? 160 : (pixels <= 360 ? 360 : 720);

  static String _key(int id, ArtworkType type, int size) => '${type.name}_${id}_$size';

  static bool _isFresh(String key) {
    if (!_cache.containsKey(key)) return false;
    if (_cache[key] != null) return true;
    final missed = _missedAt[key];
    return missed != null && DateTime.now().difference(missed) < _missRetry;
  }

  /// Cached bytes if already resolved (null value = no artwork), else absent.
  static ({bool hit, Uint8List? bytes}) peek(int id, ArtworkType type, int size) {
    final key = _key(id, type, size);
    if (!_isFresh(key)) return (hit: false, bytes: null);
    final bytes = _cache.remove(key);
    _cache[key] = bytes; // mark as recently used
    return (hit: true, bytes: bytes);
  }

  static Future<Uint8List?> load(int id, ArtworkType type, int size) {
    final key = _key(id, type, size);
    if (_isFresh(key)) return Future.value(_cache[key]);
    return _pending[key] ??= _fetch(id, type, size).then((bytes) {
      _pending.remove(key);
      _cache.remove(key);
      _cache[key] = bytes;
      if (bytes == null) {
        _missedAt[key] = DateTime.now();
      } else {
        _missedAt.remove(key);
      }
      if (_cache.length > _maxEntries) {
        final oldest = _cache.keys.first;
        _cache.remove(oldest);
        _missedAt.remove(oldest);
      }
      return bytes;
    });
  }

  static Future<T> _throttled<T>(Future<T> Function() task) async {
    if (_running >= _maxConcurrent) {
      final slot = Completer<void>();
      _waiting.add(slot);
      await slot.future;
    }
    _running++;
    try {
      return await task();
    } finally {
      _running--;
      if (_waiting.isNotEmpty) _waiting.removeFirst().complete();
    }
  }

  /// Local sources run under the concurrency limit; the online lookup is
  /// paced on its own so it never holds up covers that are on the device.
  static Future<Uint8List?> _fetch(int id, ArtworkType type, int size) async {
    final local = await _throttled(() => _fetchLocal(id, type, size));
    if (local != null || !onlineEnabled) return local;
    final song = type == ArtworkType.AUDIO ? _songs[id] : null;
    if (song == null) return null;
    final raw = await OnlineArtwork.fetch(song);
    if (raw == null || raw.isEmpty) return null;
    return _downscale(raw, size);
  }

  static Future<Uint8List?> _fetchLocal(int id, ArtworkType type, int size) async {
    try {
      final bytes = await _query.queryArtwork(
        id,
        type,
        format: ArtworkFormat.JPEG,
        size: size,
        quality: 90,
      );
      if (bytes != null && bytes.isNotEmpty) return bytes;
    } catch (_) {
      // fall through to the embedded tags
    }

    final song = type == ArtworkType.AUDIO ? _songs[id] : null;
    if (song == null) return null;
    final raw = await EmbeddedArtwork.read(song.path) ??
        await FolderArtwork.read(song.path);
    if (raw == null || raw.isEmpty) return null;
    return _downscale(raw, size);
  }

  /// Re-encodes large embedded covers at the bucket size to bound memory.
  static Future<Uint8List?> _downscale(Uint8List raw, int size) async {
    try {
      final codec = await ui.instantiateImageCodec(raw, targetWidth: size);
      final frame = await codec.getNextFrame();
      final image = frame.image;
      if (image.width >= size * 0.9 && raw.lengthInBytes < 400 * 1024) {
        image.dispose();
        return raw; // already small enough
      }
      final data = await image.toByteData(format: ui.ImageByteFormat.png);
      image.dispose();
      return data?.buffer.asUint8List();
    } catch (_) {
      return null; // not a decodable image
    }
  }
}
