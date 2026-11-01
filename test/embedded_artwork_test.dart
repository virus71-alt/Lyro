import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:lyro/data/services/embedded_artwork.dart';

final _jpeg = Uint8List.fromList([0xFF, 0xD8, 0xFF, 0xE0, 1, 2, 3, 4, 5, 0xFF, 0xD9]);

List<int> _be32(int v) => [(v >> 24) & 0xFF, (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF];
List<int> _syncsafe(int v) => [(v >> 21) & 0x7F, (v >> 14) & 0x7F, (v >> 7) & 0x7F, v & 0x7F];

/// ID3v2 tag with a TIT2 frame, then an APIC frame (picture type [picType]).
Uint8List _mp3({required int version, int picType = 3, int encoding = 0}) {
  final desc = encoding == 1 ? [0xFF, 0xFE, 0x41, 0x00, 0x00, 0x00] : [0x41, 0x00];
  final apic = [encoding, ...ascii.encode('image/jpeg'), 0, picType, ...desc, ..._jpeg];
  final title = [3, ...ascii.encode('Song')];
  List<int> frame(String id, List<int> body) => [
        ...ascii.encode(id),
        ...(version == 4 ? _syncsafe(body.length) : _be32(body.length)),
        0, 0,
        ...body,
      ];
  final frames = [...frame('TIT2', title), ...frame('APIC', apic)];
  return Uint8List.fromList([
    ...ascii.encode('ID3'), version, 0, 0, ..._syncsafe(frames.length),
    ...frames,
    0xFF, 0xFB, 0x90, 0x00, // first audio frame
  ]);
}

Uint8List _flac() {
  final mime = ascii.encode('image/jpeg');
  final pic = [
    ..._be32(3), ..._be32(mime.length), ...mime, ..._be32(0),
    ..._be32(500), ..._be32(500), ..._be32(24), ..._be32(0),
    ..._be32(_jpeg.length), ..._jpeg,
  ];
  final streamInfo = List.filled(34, 0);
  return Uint8List.fromList([
    ...ascii.encode('fLaC'),
    0x00, 0, 0, 34, ...streamInfo, // STREAMINFO, not last
    0x86, (pic.length >> 16) & 0xFF, (pic.length >> 8) & 0xFF, pic.length & 0xFF, ...pic,
  ]);
}

void main() {
  late Directory dir;
  setUp(() => dir = Directory.systemTemp.createTempSync('lyro_art'));
  tearDown(() => dir.deleteSync(recursive: true));

  Future<Uint8List?> read(Uint8List bytes) {
    final f = File('${dir.path}/t.bin')..writeAsBytesSync(bytes);
    return EmbeddedArtwork.read(f.path);
  }

  test('ID3v2.3 APIC front cover', () async {
    expect(await read(_mp3(version: 3)), _jpeg);
  });

  test('ID3v2.4 APIC with syncsafe frame sizes', () async {
    expect(await read(_mp3(version: 4)), _jpeg);
  });

  test('ID3 APIC with UTF-16 description', () async {
    expect(await read(_mp3(version: 3, encoding: 1)), _jpeg);
  });

  test('non-front-cover picture is used as a fallback', () async {
    expect(await read(_mp3(version: 3, picType: 0)), _jpeg);
  });

  test('FLAC PICTURE block', () async {
    expect(await read(_flac()), _jpeg);
  });

  test('file without artwork returns null', () async {
    final noArt = Uint8List.fromList([...ascii.encode('ID3'), 3, 0, 0, 0, 0, 0, 0, 0xFF, 0xFB]);
    expect(await read(noArt), isNull);
  });
}
