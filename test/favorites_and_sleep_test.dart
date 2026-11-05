import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lyro/core/theme/nothing_colors.dart';
import 'package:lyro/core/theme/nothing_theme.dart';
import 'package:lyro/data/models/folder_model.dart';
import 'package:lyro/data/models/song_model.dart';
import 'package:lyro/data/repositories/music_repository.dart';
import 'package:lyro/data/repositories/preferences_repository.dart';
import 'package:lyro/features/library/providers/library_provider.dart';
import 'package:lyro/features/library/screens/folders_screen.dart';
import 'package:lyro/features/library/widgets/folder_tile.dart';
import 'package:lyro/features/player/providers/player_provider.dart';
import 'package:lyro/features/player/widgets/sleep_timer_chips.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _song = Song(id: 1, title: 'Song', artist: 'Artist', album: 'Album', duration: 60000, path: '/a.flac', format: 'FLAC');

/// Just enough of the player for the Folders screen: the heart count.
class _Hearts extends ChangeNotifier implements PlayerProvider {
  int count = 0;

  void set(int v) {
    count = v;
    notifyListeners();
  }

  @override
  int get favoriteCount => count;
  @override
  Song? get currentSong => null;
  @override
  bool get isPlaying => false;
  @override
  Duration get duration => Duration.zero;
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Library extends LibraryProvider {
  List<Song> hearted = [];
  _Library(PreferencesRepository prefs) : super(musicRepo: MusicRepository(), prefsRepo: prefs);

  @override
  List<Song> get favoriteSongs => hearted;
  @override
  List<MusicFolder> get folders => const [MusicFolder(path: '/storage/emulated/0/Music/Hindi', name: 'Hindi', songs: [_song])];
}

void main() {
  testWidgets('sleep chips show the choice and report taps', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    int? tapped;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SleepTimerChips(selected: 2, line: Colors.grey, display: Colors.white, onSelect: (i) => tapped = i),
        ),
      ),
    );
    expect(tester.takeException(), isNull);
    for (final label in SleepTimerChips.labels) {
      expect(find.text(label), findsOneWidget);
    }
    final chosen = tester.widget<Container>(
      find.descendant(of: find.byKey(const Key('sleep_chip_2')), matching: find.byType(Container)),
    );
    expect((chosen.decoration! as BoxDecoration).color, NothingColors.signalRed);

    await tester.tap(find.byKey(const Key('sleep_chip_4')));
    expect(tapped, 4);
  });

  testWidgets('Favorites folder appears with the first heart, above real folders, and goes with the last', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final library = _Library(await PreferencesRepository.init());
    final hearts = _Hearts();

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider<LibraryProvider>.value(value: library),
          ChangeNotifierProvider<PlayerProvider>.value(value: hearts),
        ],
        child: MaterialApp(theme: NothingTheme.darkTheme, home: const FoldersScreen()),
      ),
    );
    await tester.pump();
    expect(find.byKey(const Key('favorites_folder')), findsNothing);
    expect(find.text('1 FOLDERS  ·  1 TRACKS  ·  1M'), findsOneWidget);

    library.hearted = [_song];
    hearts.set(1);
    await tester.pump();
    expect(find.byKey(const Key('favorites_folder')), findsOneWidget);
    expect(find.byType(FavoritesCover), findsOneWidget);
    expect(find.text('HEARTED SONGS'), findsOneWidget);
    expect(find.text('Hindi'), findsOneWidget);
    expect(find.text('♥'), findsOneWidget);
    expect(find.text('01'), findsNWidgets(3)); // Hindi keeps its number; two rows count 1 track
    expect(find.text('1 FOLDERS  ·  1 TRACKS  ·  1M'), findsOneWidget); // not double counted
    expect(
      tester.getTopLeft(find.byKey(const Key('favorites_folder'))).dy,
      lessThan(tester.getTopLeft(find.text('Hindi')).dy),
    );

    await tester.tap(find.byKey(const Key('favorites_folder')));
    await tester.pumpAndSettle();
    expect(find.text('FAVORITES'), findsOneWidget);
    expect(find.text('Song'), findsOneWidget);

    // Unheart from inside: the list empties there and the row goes when back.
    library.hearted = [];
    hearts.set(0);
    await tester.pump();
    expect(find.text('NO FAVORITES YET'), findsOneWidget);
    expect(tester.takeException(), isNull);

    final outPath = Platform.environment['FAV_OUT'];
    if (outPath != null) {
      await tester.pumpWidget(
        MaterialApp(
          theme: NothingTheme.darkTheme,
          home: Scaffold(
            body: RepaintBoundary(
              key: const Key('shot'),
              child: Container(
                color: Colors.black,
                padding: const EdgeInsets.all(16),
                child: FolderTile(
                  folder: const MusicFolder(path: '', name: 'Favorites', songs: [_song]),
                  indexLabel: '♥',
                  cover: const FavoritesCover(),
                  subtitle: 'HEARTED SONGS',
                  onTap: () {},
                ),
              ),
            ),
          ),
        ),
      );
      await tester.runAsync(() async {
        final b = tester.renderObject<RenderRepaintBoundary>(find.byKey(const Key('shot')));
        final im = await b.toImage(pixelRatio: 2);
        final d = await im.toByteData(format: ui.ImageByteFormat.png);
        File(outPath).writeAsBytesSync(d!.buffer.asUint8List());
      });
    }
  });
}
