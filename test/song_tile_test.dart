import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lyro/core/theme/nothing_theme.dart';
import 'package:lyro/data/models/song_model.dart';
import 'package:lyro/features/library/widgets/song_tile.dart';
import 'package:lyro/features/player/providers/player_provider.dart';
import 'package:provider/provider.dart';

class _IdlePlayer extends ChangeNotifier implements PlayerProvider {
  @override
  Song? get currentSong => null;
  @override
  bool get isPlaying => false;
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  testWidgets('play count sits under the duration, not on top of it', (tester) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    const song = Song(id: 1, title: 'A very long song title that must ellipsize before the duration', artist: 'Artist', album: 'Album', duration: 322000, path: '/a.mp3', format: 'MP3');
    await tester.pumpWidget(
      ChangeNotifierProvider<PlayerProvider>.value(
        value: _IdlePlayer(),
        child: MaterialApp(
          theme: NothingTheme.darkTheme,
          home: const Scaffold(
            body: SongTile(song: song, playlist: [song], durationCaption: Text('12×')),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    final duration = tester.getRect(find.text('05:22'));
    final count = tester.getRect(find.text('12×'));
    expect(count.overlaps(duration), isFalse);
    expect(count.top, greaterThanOrEqualTo(duration.bottom));
    expect(count.right, lessThanOrEqualTo(tester.getRect(find.byIcon(Icons.more_vert)).left));
  });
}
