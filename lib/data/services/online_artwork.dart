import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import '../models/song_model.dart';

/// Looks up missing covers in Apple's public iTunes Search catalogue.
///
/// Only the song's title, artist and album are sent. A cover is accepted
/// only when the title matches and the artist or album agrees, and is saved
/// on the device so each song (or album) goes online at most once. Misses
/// are remembered for [_missTtl] so the catalogue isn't asked repeatedly.
class OnlineArtwork {
  OnlineArtwork._();

  static const Duration _missTtl = Duration(days: 7);
  static const Duration _minGap = Duration(milliseconds: 1500); // API rate limit
  static const Duration _timeout = Duration(seconds: 10);
  static const int _artSize = 600;

  static Directory? _dir;
  static Future<void> _queue = Future.value();
  static DateTime _lastRequest = DateTime.fromMillisecondsSinceEpoch(0);
  static final Map<String, Future<Uint8List?>> _inFlight = {};

  static Future<Uint8List?> fetch(Song song) {
    final key = _cacheKey(song);
    if (key == null) return Future.value(null);
    return _inFlight[key] ??= _fetch(song, key).whenComplete(() => _inFlight.remove(key));
  }

  /// The cover URL the catalogue match would use (no download, no cache).
  @visibleForTesting
  static Future<String?> findArtworkUrl(Song song) => _serialized(() => _search(song));

  static Future<Uint8List?> _fetch(Song song, String key) async {
    final dir = await _cacheDir();
    final hit = File('${dir.path}/$key.jpg');
    if (await hit.exists()) return hit.readAsBytes();
    final miss = File('${dir.path}/$key.none');
    if (await miss.exists() &&
        DateTime.now().difference(await miss.lastModified()) < _missTtl) {
      return null;
    }

    try {
      final url = await _serialized(() => _search(song));
      if (url == null) {
        await miss.writeAsString('');
        return null;
      }
      final bytes = await _serialized(() => _get(url));
      if (bytes == null || bytes.isEmpty) return null;
      await hit.writeAsBytes(bytes, flush: true);
      if (await miss.exists()) await miss.delete();
      return bytes;
    } catch (_) {
      return null; // offline or API error: try again another time
    }
  }

  /// Runs network calls one at a time, spaced by [_minGap].
  static Future<T> _serialized<T>(Future<T> Function() task) {
    final run = _queue.then((_) async {
      final wait = _minGap - DateTime.now().difference(_lastRequest);
      if (wait > Duration.zero) await Future<void>.delayed(wait);
      _lastRequest = DateTime.now();
      return task();
    });
    _queue = run.then((_) {}, onError: (_) {});
    return run;
  }

  static Future<String?> _search(Song song) async {
    final title = _norm(song.title);
    if (title.isEmpty) return null;
    final artist = _meaningful(song.artist) ? song.artist : '';
    final album = _meaningful(song.album) ? song.album : '';

    for (final country in ['US', 'IN']) {
      final uri = Uri.https('itunes.apple.com', '/search', {
        'term': '${song.title} $artist'.trim(),
        'entity': 'song',
        'limit': '15',
        'country': country,
      });
      final body = await _get(uri.toString());
      if (body == null) continue;
      final results = (jsonDecode(utf8.decode(body))['results'] as List?) ?? const [];

      String? best;
      var bestScore = 0;
      for (final r in results.whereType<Map<String, dynamic>>()) {
        final score = _score(r, title: title, artist: artist, album: album);
        if (score > bestScore) {
          bestScore = score;
          best = r['artworkUrl100'] as String?;
        }
      }
      // Title must match, plus artist or album agreement
      if (best != null && bestScore >= 5) {
        return best.replaceAll(RegExp(r'\d+x\d+bb'), '${_artSize}x${_artSize}bb');
      }
    }
    return null;
  }

  static int _score(
    Map<String, dynamic> r, {
    required String title,
    required String artist,
    required String album,
  }) {
    final track = _norm(r['trackName'] as String? ?? '');
    final rArtist = _norm(r['artistName'] as String? ?? '');
    final rAlbum = _norm(r['collectionName'] as String? ?? '');
    var score = 0;
    if (track == title) {
      score += 4;
    } else if (track.startsWith(title) || title.startsWith(track)) {
      score += 3;
    } else {
      return 0;
    }
    if (artist.isNotEmpty) {
      final words = _norm(artist).split(' ').where((w) => w.length > 2);
      if (words.any(rArtist.contains)) score += 2;
    }
    if (album.isNotEmpty) {
      final a = _norm(album);
      if (rAlbum == a) {
        score += 3;
      } else if (rAlbum.contains(a) || a.contains(rAlbum)) {
        score += 2;
      }
    }
    return score;
  }

  static Future<Uint8List?> _get(String url) async {
    final client = HttpClient()..connectionTimeout = _timeout;
    try {
      final req = await client.getUrl(Uri.parse(url)).timeout(_timeout);
      final res = await req.close().timeout(_timeout);
      if (res.statusCode != 200) return null;
      final builder = BytesBuilder(copy: false);
      await for (final chunk in res.timeout(_timeout)) {
        builder.add(chunk);
      }
      return builder.takeBytes();
    } finally {
      client.close(force: true);
    }
  }

  static Future<Directory> _cacheDir() async {
    final cached = _dir;
    if (cached != null) return cached;
    final base = await getApplicationSupportDirectory();
    final dir = Directory('${base.path}/artwork_online');
    await dir.create(recursive: true);
    return _dir = dir;
  }

  static bool _meaningful(String s) {
    final n = s.trim().toLowerCase();
    return n.isNotEmpty && !n.startsWith('unknown') && n != '<unknown>';
  }

  /// Lower-case, drop bracketed parts ("(Title Track)", "[Remastered]") and
  /// punctuation, collapse spaces.
  static String _norm(String s) => s
      .toLowerCase()
      .replaceAll(RegExp(r'[\(\[][^\)\]]*[\)\]]'), ' ')
      .replaceAll(RegExp(r'[^\p{L}\p{N}]+', unicode: true), ' ')
      .trim()
      .replaceAll(RegExp(r'\s+'), ' ');

  /// One cache entry per album when the album is known, else per song.
  static String? _cacheKey(Song song) {
    final artist = _norm(song.artist);
    final album = _meaningful(song.album) ? _norm(song.album) : '';
    final title = _norm(song.title);
    if (title.isEmpty) return null;
    final raw = album.isNotEmpty && album != title
        ? 'a|$artist|$album'
        : 't|$artist|$title';
    // FNV-1a 64-bit → stable file name
    var h = 0xcbf29ce484222325;
    for (final c in utf8.encode(raw)) {
      h ^= c;
      h = (h * 0x100000001b3) & 0xFFFFFFFFFFFFFFFF;
    }
    return h.toUnsigned(64).toRadixString(16).padLeft(16, '0');
  }
}
