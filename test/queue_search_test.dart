import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lyro/core/theme/nothing_theme.dart';
import 'package:lyro/data/models/song_model.dart';
import 'package:lyro/features/player/providers/player_provider.dart';
import 'package:lyro/features/player/widgets/queue_sheet.dart';
import 'package:provider/provider.dart';

Song _song(int id, String title, String artist) =>
    Song(id: id, title: title, artist: artist, album: 'Album $id', duration: 60000, path: '/$id.flac', format: 'FLAC');

class _Player extends ChangeNotifier implements PlayerProvider {
  final tapped = <int>[];

  @override
  final List<Song> queue = [_song(1, 'Gerua', 'Arijit Singh'), _song(2, 'Hagupit', 'Ichiko'), _song(3, 'Channa', 'Arijit Singh')];
  @override
  int get queueIndex => 0;
  @override
  String get queueSource => 'Quick picks';
  @override
  int get priorityEnd => 3;
  @override
  bool get isPlaying => false;
  @override
  Future<void> skipToQueueItem(int index) async => tapped.add(index);
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  testWidgets('search filters the queue and plays the right song', (tester) async {
    final player = _Player();
    await tester.pumpWidget(
      ChangeNotifierProvider<PlayerProvider>.value(
        value: player,
        child: MaterialApp(
          theme: NothingTheme.darkTheme,
          home: Scaffold(
            body: QueueSheet(background: Colors.black, onDragUpdate: (_) {}, onDragEnd: (_) {}),
          ),
        ),
      ),
    );
    await tester.pump();
    expect(find.byKey(const Key('queue_search_field')), findsNothing);

    await tester.tap(find.byKey(const Key('queue_search')));
    await tester.pump();

    // By artist, ignoring case
    await tester.enterText(find.byKey(const Key('queue_search_field')), 'ARIJIT');
    await tester.pump();
    expect(find.text('Gerua'), findsOneWidget);
    expect(find.text('Channa'), findsOneWidget);
    expect(find.text('Hagupit'), findsNothing);

    // Tapping a result plays its real queue position, not its list position
    await tester.tap(find.text('Channa'));
    await tester.pump();
    expect(player.tapped, [2]);

    await tester.enterText(find.byKey(const Key('queue_search_field')), 'zzz');
    await tester.pump();
    expect(find.text('NO MATCHES'), findsOneWidget);

    // Closing search brings everything back
    await tester.tap(find.byKey(const Key('queue_search')));
    await tester.pump();
    expect(find.byKey(const Key('queue_search_field')), findsNothing);
    expect(find.text('Hagupit'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
