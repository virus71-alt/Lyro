import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:lyro/data/services/folder_artwork.dart';

void main() {
  late Directory dir;
  setUp(() {
    dir = Directory.systemTemp.createTempSync('lyro_folder');
    FolderArtwork.clear();
  });
  tearDown(() => dir.deleteSync(recursive: true));

  File put(String name, List<int> bytes) => File('${dir.path}/$name')..writeAsBytesSync(bytes);

  test('uses folder.jpg for songs in the folder', () async {
    final song = put('01 - Song.flac', [0]);
    put('folder.jpg', [1, 1]);
    expect(await FolderArtwork.read(song.path), [1, 1]);
  });

  test('prefers an image named after the song', () async {
    final song = put('01 - Yeh Aaina - .flac', [0]);
    put('folder.jpg', [1]);
    put('01 - Yeh Aaina - .flac.png', [2]);
    expect(await FolderArtwork.read(song.path), [2]);
  });

  test('ignores unrelated photos', () async {
    final song = put('Aashish - Bipul Chettri.mp3', [0]);
    put('IMG_0029.JPG', [9]);
    put('image.png', [9]);
    expect(await FolderArtwork.read(song.path), isNull);
  });

  test('matches album-art names case-insensitively', () async {
    final song = put('track.mp3', [0]);
    put('Cover.PNG', [3]);
    expect(await FolderArtwork.read(song.path), [3]);
  });

  test('mixed-album folder: folder.jpg is ignored, song-named image still used', () async {
    final song = put('Raazi - 01 - Ae Watan.flac', [0]);
    final other = put('01 - Yeh Aaina - .flac', [0]);
    put('folder.jpg', [1]); // belongs to some other album in this folder
    put('01 - Yeh Aaina - .flac.png', [2]);
    expect(await FolderArtwork.read(song.path, singleAlbumFolder: false), isNull);
    expect(await FolderArtwork.read(other.path, singleAlbumFolder: false), [2]);
  });
}
