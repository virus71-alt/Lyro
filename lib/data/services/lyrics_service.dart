import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:path_provider/path_provider.dart';

import '../models/song_model.dart';

class LyricLine {
  final Duration time;
  final String text;
  const LyricLine(this.time, this.text);
}

class Lyrics {
  final List<LyricLine> lines;
  final bool synced;
  final String source; // 'file', 'embedded', 'lrclib'

  const Lyrics({required this.lines, required this.synced, required this.source});

  bool get isEmpty => lines.every((l) => l.text.trim().isEmpty);

  /// Index of the line being sung at [position] (-1 before the first).
  int indexAt(Duration position) {
    if (!synced) return -1;
    var lo = 0, hi = lines.length - 1, found = -1;
    while (lo <= hi) {
      final mid = (lo + hi) >> 1;
      if (lines[mid].time <= position) {
        found = mid;
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return found;
  }

  /// Parses LRC ("[mm:ss.xx] text", several stamps per line allowed); text
  /// without timestamps becomes unsynced lyrics.
  static Lyrics? parse(String raw, String source) {
    final stamp = RegExp(r'\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]');
    final synced = <LyricLine>[];
    final plain = <LyricLine>[];
    for (final line in const LineSplitter().convert(raw)) {
      final stamps = stamp.allMatches(line).toList();
      final text = line.replaceAll(stamp, '').trim();
      if (stamps.isEmpty) {
        // Skip LRC metadata tags like [ar:...] / [ti:...]
        if (!RegExp(r'^\[[a-zA-Z]+:.*\]$').hasMatch(line.trim())) {
          plain.add(LyricLine(Duration.zero, line.trim()));
        }
        continue;
      }
      for (final m in stamps) {
        final frac = m.group(3) ?? '0';
        final ms = int.parse(frac.padRight(3, '0').substring(0, 3));
        synced.add(LyricLine(
          Duration(minutes: int.parse(m.group(1)!), seconds: int.parse(m.group(2)!), milliseconds: ms),
          text,
        ));
      }
    }
    if (synced.isNotEmpty) {
      synced.sort((a, b) => a.time.compareTo(b.time));
      return Lyrics(lines: synced, synced: true, source: source);
    }
    while (plain.isNotEmpty && plain.first.text.isEmpty) {
      plain.removeAt(0);
    }
    if (plain.isEmpty) return null;
    return Lyrics(lines: plain, synced: false, source: source);
  }
}

/// Finds lyrics for a song: a `.lrc` file beside it, lyrics embedded in its
/// tags (ID3 USLT, Vorbis LYRICS, MP4 ©lyr), or — when [onlineEnabled] —
/// LRCLIB (lrclib.net), cached on the device.
class LyricsService {
  LyricsService._();

  static bool onlineEnabled = true;
  static const Duration _missTtl = Duration(days: 7);
  static final Map<String, Future<Lyrics?>> _memo = {};

  static Future<Lyrics?> forSong(Song song) =>
      _memo[song.path] ??= _find(song).then((l) {
        if (l == null) _memo.remove(song.path); // allow a later retry
        return l;
      });

  static void forget() => _memo.clear();

  static Future<Lyrics?> _find(Song song) async {
    final path = song.path;
    final local = await Isolate.run(() => _readLocal(path));
    if (local != null) {
      final l = Lyrics.parse(local.$1, local.$2);
      if (l != null && !l.isEmpty) return l;
    }
    if (!onlineEnabled) return null;
    final online = await _lrclib(song);
    if (online == null) return null;
    final l = Lyrics.parse(online, 'lrclib');
    return l == null || l.isEmpty ? null : l;
  }

  // ---------------------------------------------------------------- local

  static (String, String)? _readLocal(String path) {
    if (path.isEmpty) return null;
    // 1. Sidecar .lrc (may be unreadable under scoped storage; that's fine)
    try {
      final dot = path.lastIndexOf('.');
      final base = dot > 0 ? path.substring(0, dot) : path;
      for (final ext in ['.lrc', '.LRC', '.txt']) {
        final f = File('$base$ext');
        if (f.existsSync()) return (f.readAsStringSync(), 'file');
      }
    } catch (_) {}
    // 2. Embedded tags
    try {
      final text = _embedded(path);
      if (text != null && text.trim().isNotEmpty) return (text, 'embedded');
    } catch (_) {}
    return null;
  }

  static String? _embedded(String path) {
    final raf = File(path).openSync();
    try {
      final head = raf.readSync(12);
      String tag(int s, int l) => String.fromCharCodes(head.sublist(s, s + l));
      if (head.length < 8) return null;
      if (tag(0, 3) == 'ID3') return _id3Uslt(raf, head);
      if (tag(0, 4) == 'fLaC') return _flacComments(raf);
      if (tag(0, 4) == 'OggS') return _oggComments(raf);
      if (tag(4, 4) == 'ftyp') return _mp4Lyrics(raf);
      return null;
    } finally {
      raf.closeSync();
    }
  }

  static int _be32(List<int> b, int o) => (b[o] << 24) | (b[o + 1] << 16) | (b[o + 2] << 8) | b[o + 3];
  static int _le32(List<int> b, int o) => b[o] | (b[o + 1] << 8) | (b[o + 2] << 16) | (b[o + 3] << 24);
  static int _syncsafe(List<int> b, int o) =>
      (b[o] & 0x7F) << 21 | (b[o + 1] & 0x7F) << 14 | (b[o + 2] & 0x7F) << 7 | (b[o + 3] & 0x7F);

  static String? _id3Uslt(RandomAccessFile raf, Uint8List head) {
    final major = head[3];
    final size = _syncsafe(head, 6);
    if (major < 3 || size <= 0 || size > 16 << 20) return null;
    raf.setPositionSync(10);
    final tag = raf.readSync(size);
    var pos = 0;
    while (pos + 10 <= tag.length) {
      if (tag[pos] == 0) break;
      final id = String.fromCharCodes(tag.sublist(pos, pos + 4));
      final len = major == 4 ? _syncsafe(tag, pos + 4) : _be32(tag, pos + 4);
      final start = pos + 10, end = start + len;
      if (len <= 0 || end > tag.length) break;
      if (id == 'USLT' && len > 5) {
        final f = tag.sublist(start, end);
        final enc = f[0];
        var p = 4; // encoding + 3-byte language
        if (enc == 1 || enc == 2) {
          while (p + 1 < f.length && !(f[p] == 0 && f[p + 1] == 0)) {
            p += 2;
          }
          p += 2;
          final body = f.sublist(p);
          return _utf16(body, bigEndian: enc == 2);
        }
        while (p < f.length && f[p] != 0) {
          p++;
        }
        p++;
        final body = f.sublist(p);
        return enc == 3 ? utf8.decode(body, allowMalformed: true) : latin1.decode(body);
      }
      pos = end;
    }
    return null;
  }

  static String _utf16(List<int> b, {required bool bigEndian}) {
    var i = 0;
    var be = bigEndian;
    if (b.length >= 2 && b[0] == 0xFF && b[1] == 0xFE) {
      be = false;
      i = 2;
    } else if (b.length >= 2 && b[0] == 0xFE && b[1] == 0xFF) {
      be = true;
      i = 2;
    }
    final units = <int>[];
    for (; i + 1 < b.length; i += 2) {
      final u = be ? (b[i] << 8) | b[i + 1] : b[i] | (b[i + 1] << 8);
      if (u == 0) break;
      units.add(u);
    }
    return String.fromCharCodes(units);
  }

  /// Vorbis comment block → value of LYRICS / UNSYNCEDLYRICS / SYNCEDLYRICS.
  static String? _vorbisLyrics(List<int> c, int start) {
    var p = start;
    if (p + 4 > c.length) return null;
    p += 4 + _le32(c, p);
    if (p + 4 > c.length) return null;
    final count = _le32(c, p);
    p += 4;
    for (var i = 0; i < count && p + 4 <= c.length; i++) {
      final len = _le32(c, p);
      p += 4;
      if (p + len > c.length) return null;
      final entry = utf8.decode(c.sublist(p, p + len), allowMalformed: true);
      final eq = entry.indexOf('=');
      if (eq > 0) {
        final key = entry.substring(0, eq).toUpperCase();
        if (key == 'LYRICS' || key == 'UNSYNCEDLYRICS' || key == 'SYNCEDLYRICS') {
          return entry.substring(eq + 1);
        }
      }
      p += len;
    }
    return null;
  }

  static String? _flacComments(RandomAccessFile raf) {
    raf.setPositionSync(4);
    while (true) {
      final h = raf.readSync(4);
      if (h.length < 4) return null;
      final last = h[0] & 0x80 != 0;
      final type = h[0] & 0x7F;
      final len = (h[1] << 16) | (h[2] << 8) | h[3];
      if (type == 4) return _vorbisLyrics(raf.readSync(len), 0);
      raf.setPositionSync(raf.positionSync() + len);
      if (last) return null;
    }
  }

  static String? _oggComments(RandomAccessFile raf) {
    raf.setPositionSync(0);
    final packets = <BytesBuilder>[BytesBuilder(copy: false)];
    var read = 0;
    while (packets.length < 3 && read < 16 << 20) {
      final h = raf.readSync(27);
      if (h.length < 27 || String.fromCharCodes(h.sublist(0, 4)) != 'OggS') break;
      final table = raf.readSync(h[26]);
      final payload = raf.readSync(table.fold<int>(0, (a, b) => a + b));
      read += 27 + table.length + payload.length;
      var off = 0;
      for (final seg in table) {
        packets.last.add(Uint8List.sublistView(payload, off, off + seg));
        off += seg;
        if (seg < 255) packets.add(BytesBuilder(copy: false));
      }
    }
    if (packets.length < 2) return null;
    final c = packets[1].takeBytes();
    final start = c.length > 8 && String.fromCharCodes(c.sublist(0, 8)) == 'OpusTags' ? 8 : 7;
    return _vorbisLyrics(c, start);
  }

  static String? _mp4Lyrics(RandomAccessFile raf) {
    final length = raf.lengthSync();
    var pos = 0;
    while (pos + 8 <= length) {
      raf.setPositionSync(pos);
      final h = raf.readSync(8);
      var size = _be32(h, 0);
      if (size == 0) size = length - pos;
      if (size < 8) return null;
      if (String.fromCharCodes(h.sublist(4, 8)) == 'moov') {
        if (size > 32 << 20) return null;
        final moov = raf.readSync(size - 8);
        // Find "©lyr" → "data" box (8 header + 8 type/locale)
        final key = [0xA9, 0x6C, 0x79, 0x72];
        for (var i = 4; i + 24 < moov.length; i++) {
          if (moov[i] == key[0] && moov[i + 1] == key[1] && moov[i + 2] == key[2] && moov[i + 3] == key[3]) {
            final d = i + 4;
            final dataSize = _be32(moov, d);
            if (String.fromCharCodes(moov.sublist(d + 4, d + 8)) == 'data' && d + dataSize <= moov.length) {
              return utf8.decode(moov.sublist(d + 16, d + dataSize), allowMalformed: true);
            }
          }
        }
        return null;
      }
      pos += size;
    }
    return null;
  }

  // ---------------------------------------------------------------- online

  static Future<String?> _lrclib(Song song) async {
    final title = song.title.trim();
    if (title.isEmpty) return null;
    final dir = Directory('${(await getApplicationSupportDirectory()).path}/lyrics_online');
    await dir.create(recursive: true);
    final key = _hash('${song.artist}|${song.title}|${song.duration ~/ 2000}');
    final hit = File('${dir.path}/$key.lrc');
    if (await hit.exists()) return hit.readAsString();
    final miss = File('${dir.path}/$key.none');
    if (await miss.exists() && DateTime.now().difference(await miss.lastModified()) < _missTtl) {
      return null;
    }

    try {
      final params = {
        'track_name': title,
        if (!song.artist.toLowerCase().startsWith('unknown')) 'artist_name': song.artist,
        if (!song.album.toLowerCase().startsWith('unknown')) 'album_name': song.album,
        if (song.duration > 0) 'duration': '${(song.duration / 1000).round()}',
      };
      var body = await _get(Uri.https('lrclib.net', '/api/get', params));
      Map<String, dynamic>? match = body == null ? null : jsonDecode(body) as Map<String, dynamic>?;
      if (match == null || match['statusCode'] == 404) {
        // Looser search: closest duration among title/artist matches
        body = await _get(Uri.https('lrclib.net', '/api/search', {
          'track_name': title,
          if (params['artist_name'] != null) 'artist_name': params['artist_name']!,
        }));
        final list = body == null ? const [] : (jsonDecode(body) as List? ?? const []);
        final want = song.duration / 1000;
        match = null;
        double best = 8; // seconds tolerance
        for (final r in list.whereType<Map<String, dynamic>>()) {
          final d = ((r['duration'] as num?)?.toDouble() ?? 0) - want;
          if (want == 0 || d.abs() < best) {
            best = d.abs();
            match = r;
            if (want == 0) break;
          }
        }
      }
      final text = (match?['syncedLyrics'] as String?)?.trim().isNotEmpty == true
          ? match!['syncedLyrics'] as String
          : match?['plainLyrics'] as String?;
      if (text == null || text.trim().isEmpty) {
        await miss.writeAsString('');
        return null;
      }
      await hit.writeAsString(text, flush: true);
      return text;
    } catch (_) {
      return null; // offline: retry another time
    }
  }

  static Future<String?> _get(Uri uri) async {
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 10);
    try {
      final req = await client.getUrl(uri);
      req.headers.set('User-Agent', 'Lyro/1.0 (offline music player)');
      final res = await req.close().timeout(const Duration(seconds: 12));
      final text = await res.transform(utf8.decoder).join();
      if (res.statusCode == 404) return '{"statusCode":404}';
      return res.statusCode == 200 ? text : null;
    } finally {
      client.close(force: true);
    }
  }

  static String _hash(String s) {
    var h = 0xcbf29ce484222325;
    for (final c in utf8.encode(s.toLowerCase())) {
      h ^= c;
      h = h * 0x100000001b3;
    }
    return h.toUnsigned(64).toRadixString(16).padLeft(16, '0');
  }
}
