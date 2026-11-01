import 'dart:convert';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

/// Reads the cover image embedded in an audio file's tags.
///
/// Android's MediaStore thumbnailer (what `on_audio_query` uses) fails for
/// some files that do carry artwork, so this parses the tags directly:
/// ID3v2.2/2.3/2.4 (MP3), FLAC PICTURE blocks, MP4/M4A `covr` atoms and
/// Ogg (Opus/Vorbis) METADATA_BLOCK_PICTURE comments. Runs in an isolate.
class EmbeddedArtwork {
  EmbeddedArtwork._();

  static const int _maxTagBytes = 16 * 1024 * 1024;

  static Future<Uint8List?> read(String path) {
    if (path.isEmpty) return Future.value(null);
    return Isolate.run(() => _readSync(path));
  }

  static Uint8List? _readSync(String path) {
    RandomAccessFile? raf;
    try {
      final file = File(path);
      if (!file.existsSync()) return null;
      raf = file.openSync();
      final head = raf.readSync(12);
      if (head.length < 4) return null;
      String tag(int start, int len) =>
          String.fromCharCodes(head.sublist(start, start + len));

      if (tag(0, 3) == 'ID3') return _id3(raf);
      if (tag(0, 4) == 'fLaC') return _flac(raf);
      if (tag(0, 4) == 'OggS') return _ogg(raf);
      if (head.length >= 8 && tag(4, 4) == 'ftyp') return _mp4(raf);
      return null;
    } catch (_) {
      return null;
    } finally {
      raf?.closeSync();
    }
  }

  // ---------------------------------------------------------------- ID3v2
  static Uint8List? _id3(RandomAccessFile raf) {
    raf.setPositionSync(0);
    final h = raf.readSync(10);
    final major = h[3];
    final flags = h[5];
    final size = _syncsafe(h, 6);
    if (size <= 0 || size > _maxTagBytes) return null;
    var tag = raf.readSync(size);
    if (flags & 0x80 != 0 && major < 4) tag = _unsync(tag);

    var pos = 0;
    if (flags & 0x40 != 0 && major >= 3) {
      // extended header
      final ext = major == 4 ? _syncsafe(tag, 0) : _be32(tag, 0) + 4;
      pos += ext;
    }

    Uint8List? fallback;
    final headerLen = major == 2 ? 6 : 10;
    while (pos + headerLen <= tag.length) {
      final id = String.fromCharCodes(tag.sublist(pos, pos + (major == 2 ? 3 : 4)));
      if (id.codeUnitAt(0) == 0) break;
      final frameSize = major == 2
          ? (tag[pos + 3] << 16) | (tag[pos + 4] << 8) | tag[pos + 5]
          : major == 4
              ? _syncsafe(tag, pos + 4)
              : _be32(tag, pos + 4);
      final start = pos + headerLen;
      final end = start + frameSize;
      if (frameSize <= 0 || end > tag.length) break;

      if (id == 'APIC' || id == 'PIC') {
        var frame = tag.sublist(start, end);
        if (major == 4 && tag[pos + 9] & 0x02 != 0) frame = _unsync(frame);
        final pic = _parseApic(frame, isV22: id == 'PIC');
        if (pic != null) {
          if (pic.type == 3) return pic.data; // front cover
          fallback ??= pic.data;
        }
      }
      pos = end;
    }
    return fallback;
  }

  static ({int type, Uint8List data})? _parseApic(Uint8List f, {required bool isV22}) {
    if (f.length < 4) return null;
    final enc = f[0];
    var p = 1;
    if (isV22) {
      p += 3; // image format, e.g. "JPG"
    } else {
      while (p < f.length && f[p] != 0) {
        p++;
      }
      p++; // mime terminator
    }
    if (p >= f.length) return null;
    final type = f[p++];
    // description, terminated by 0x00 (latin1/utf8) or 0x00 0x00 (utf16)
    if (enc == 1 || enc == 2) {
      while (p + 1 < f.length && !(f[p] == 0 && f[p + 1] == 0)) {
        p += 2;
      }
      p += 2;
    } else {
      while (p < f.length && f[p] != 0) {
        p++;
      }
      p++;
    }
    if (p >= f.length) return null;
    return (type: type, data: Uint8List.sublistView(f, p));
  }

  // ---------------------------------------------------------------- FLAC
  static Uint8List? _flac(RandomAccessFile raf) {
    raf.setPositionSync(4);
    Uint8List? fallback;
    while (true) {
      final h = raf.readSync(4);
      if (h.length < 4) return fallback;
      final last = h[0] & 0x80 != 0;
      final type = h[0] & 0x7F;
      final len = (h[1] << 16) | (h[2] << 8) | h[3];
      if (type == 6 && len < _maxTagBytes) {
        final pic = _parseFlacPicture(raf.readSync(len));
        if (pic != null) {
          if (pic.type == 3) return pic.data;
          fallback ??= pic.data;
        }
      } else {
        raf.setPositionSync(raf.positionSync() + len);
      }
      if (last) return fallback;
    }
  }

  static ({int type, Uint8List data})? _parseFlacPicture(Uint8List b) {
    if (b.length < 32) return null;
    var p = 0;
    final type = _be32(b, p);
    p += 4;
    final mimeLen = _be32(b, p);
    p += 4 + mimeLen;
    if (p + 4 > b.length) return null;
    final descLen = _be32(b, p);
    p += 4 + descLen + 16; // width, height, depth, colours
    if (p + 4 > b.length) return null;
    final dataLen = _be32(b, p);
    p += 4;
    if (dataLen <= 0 || p + dataLen > b.length) return null;
    return (type: type, data: Uint8List.sublistView(b, p, p + dataLen));
  }

  // ---------------------------------------------------------------- MP4
  static Uint8List? _mp4(RandomAccessFile raf) {
    final length = raf.lengthSync();
    // Walk top-level atoms (headers only) to find moov, wherever it is.
    var pos = 0;
    while (pos + 8 <= length) {
      raf.setPositionSync(pos);
      final h = raf.readSync(16);
      var size = _be32(h, 0);
      final name = String.fromCharCodes(h.sublist(4, 8));
      var headerLen = 8;
      if (size == 1 && h.length >= 16) {
        size = (_be32(h, 8) << 32) | _be32(h, 12);
        headerLen = 16;
      } else if (size == 0) {
        size = length - pos;
      }
      if (size < headerLen) return null;
      if (name == 'moov') {
        if (size > _maxTagBytes * 2) return null;
        raf.setPositionSync(pos + headerLen);
        final moov = raf.readSync(size - headerLen);
        return _findCovr(moov, 0, moov.length);
      }
      pos += size;
    }
    return null;
  }

  static Uint8List? _findCovr(Uint8List b, int start, int end) {
    const containers = {'udta', 'ilst', 'trak', 'mdia', 'minf', 'stbl'};
    var p = start;
    while (p + 8 <= end) {
      final size = _be32(b, p);
      if (size < 8 || p + size > end) return null;
      final name = String.fromCharCodes(b.sublist(p + 4, p + 8));
      if (name == 'covr') {
        // covr > data: 8-byte header + 4 type + 4 locale
        final d = p + 8;
        if (d + 16 <= p + size && String.fromCharCodes(b.sublist(d + 4, d + 8)) == 'data') {
          final dataSize = _be32(b, d);
          return Uint8List.sublistView(b, d + 16, d + dataSize);
        }
      } else if (name == 'meta') {
        final found = _findCovr(b, p + 12, p + size); // full box: +4 version/flags
        if (found != null) return found;
      } else if (containers.contains(name)) {
        final found = _findCovr(b, p + 8, p + size);
        if (found != null) return found;
      }
      p += size;
    }
    return null;
  }

  // ---------------------------------------------------------------- Ogg
  static Uint8List? _ogg(RandomAccessFile raf) {
    // Reassemble the second logical packet (the comment header) from pages.
    raf.setPositionSync(0);
    final packets = <BytesBuilder>[BytesBuilder(copy: false)];
    var read = 0;
    while (packets.length < 3 && read < _maxTagBytes) {
      final h = raf.readSync(27);
      if (h.length < 27 || String.fromCharCodes(h.sublist(0, 4)) != 'OggS') break;
      final segCount = h[26];
      final table = raf.readSync(segCount);
      final payload = raf.readSync(table.fold<int>(0, (a, b) => a + b));
      read += 27 + segCount + payload.length;
      var off = 0;
      for (final seg in table) {
        packets.last.add(Uint8List.sublistView(payload, off, off + seg));
        off += seg;
        if (seg < 255) packets.add(BytesBuilder(copy: false));
      }
    }
    if (packets.length < 2) return null;
    final c = packets[1].takeBytes();
    // Opus: "OpusTags"; Vorbis: 0x03 "vorbis"
    var p = c.length > 8 && String.fromCharCodes(c.sublist(0, 8)) == 'OpusTags' ? 8 : 7;
    if (p + 4 > c.length) return null;
    p += 4 + _le32(c, p); // vendor
    if (p + 4 > c.length) return null;
    final count = _le32(c, p);
    p += 4;
    for (var i = 0; i < count && p + 4 <= c.length; i++) {
      final len = _le32(c, p);
      p += 4;
      if (p + len > c.length) return null;
      const key = 'METADATA_BLOCK_PICTURE=';
      if (len > key.length &&
          String.fromCharCodes(c.sublist(p, p + key.length)).toUpperCase() == key) {
        try {
          final b64 = ascii.decode(c.sublist(p + key.length, p + len));
          final pic = _parseFlacPicture(base64.decode(base64.normalize(b64)));
          if (pic != null) return Uint8List.fromList(pic.data);
        } catch (_) {
          return null;
        }
      }
      p += len;
    }
    return null;
  }

  // ---------------------------------------------------------------- utils
  static int _syncsafe(Uint8List b, int o) =>
      (b[o] & 0x7F) << 21 | (b[o + 1] & 0x7F) << 14 | (b[o + 2] & 0x7F) << 7 | (b[o + 3] & 0x7F);

  static int _be32(Uint8List b, int o) =>
      (b[o] << 24) | (b[o + 1] << 16) | (b[o + 2] << 8) | b[o + 3];

  static int _le32(Uint8List b, int o) =>
      b[o] | (b[o + 1] << 8) | (b[o + 2] << 16) | (b[o + 3] << 24);

  /// Reverses ID3 unsynchronisation (0xFF 0x00 → 0xFF).
  static Uint8List _unsync(Uint8List b) {
    final out = BytesBuilder(copy: false);
    for (var i = 0; i < b.length; i++) {
      out.addByte(b[i]);
      if (b[i] == 0xFF && i + 1 < b.length && b[i + 1] == 0x00) i++;
    }
    return out.takeBytes();
  }
}
