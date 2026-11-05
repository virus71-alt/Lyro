import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lyro/core/theme/nothing_theme.dart';
import 'package:lyro/core/utils/formatters.dart';
import 'package:lyro/data/models/folder_model.dart';
import 'package:lyro/data/models/song_model.dart';
import 'package:lyro/features/library/widgets/folder_tile.dart';

Song _song(int id, int minutes) => Song(
      id: id,
      title: 'Song $id',
      artist: 'Artist',
      album: 'Album',
      duration: minutes * 60000,
      path: '/storage/emulated/0/Music/English/$id.flac',
      format: 'FLAC',
    );

void main() {
  test('play time reads in hours and minutes', () {
    expect(Formatters.formatPlayTime(null), '0M');
    expect(Formatters.formatPlayTime(45 * 60000), '45M');
    expect(Formatters.formatPlayTime(60 * 60000), '1H 00M');
    expect(Formatters.formatPlayTime(754 * 60000), '12H 34M');
  });

  test('a folder adds up its songs', () {
    final folder = MusicFolder(path: '/a', name: 'a', songs: [_song(1, 50), _song(2, 42)]);
    expect(folder.totalDuration, 92 * 60000);
  });

  testWidgets('folder row shows play time beside the track count, on a narrow phone', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(
        theme: NothingTheme.darkTheme,
        home: Scaffold(
          body: FolderTile(
            folder: MusicFolder(
              path: '/storage/emulated/0/Music/A very long folder name that must ellipsize',
              name: 'A very long folder name that must ellipsize',
              songs: [_song(1, 754)],
            ),
            onTap: () {},
          ),
        ),
      ),
    );
    expect(tester.takeException(), isNull);
    expect(find.text('TRACK  ·  12H 34M'), findsOneWidget);
  });
}
