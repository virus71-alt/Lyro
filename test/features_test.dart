import 'package:flutter_test/flutter_test.dart';
import 'package:lyro/data/models/song_model.dart';
import 'package:lyro/data/repositories/preferences_repository.dart';
import 'package:lyro/data/services/lyrics_service.dart';
import 'package:lyro/features/library/screens/smart_list_screen.dart';
import 'package:lyro/features/library/widgets/home_sections.dart';
import 'package:lyro/features/playlists/providers/playlist_provider.dart';
import 'package:lyro/features/player/providers/player_provider.dart';
import 'package:lyro/shared/widgets/alphabet_scroll_list.dart';
import 'package:lyro/shared/widgets/dot_toast.dart';
import 'package:shared_preferences/shared_preferences.dart';

Song song(int id, {String title = 't', int dateAdded = 0}) => Song(
      id: id,
      title: title,
      artist: 'a',
      album: 'b',
      duration: 1000,
      path: '/$id',
      format: 'MP3',
      dateAdded: dateAdded,
    );

void main() {
  group('Lyrics', () {
    test('parses synced LRC with metadata and multiple stamps', () {
      final l = Lyrics.parse('[ar:Someone]\n[00:01.00]One\n[00:05.50][00:20.00]Chorus\n[00:10]Two', 'file')!;
      expect(l.synced, isTrue);
      expect(l.lines.map((e) => e.text), ['One', 'Chorus', 'Two', 'Chorus']);
      expect(l.indexAt(const Duration(milliseconds: 500)), -1);
      expect(l.indexAt(const Duration(seconds: 6)), 1);
      expect(l.indexAt(const Duration(seconds: 12)), 2);
      expect(l.indexAt(const Duration(minutes: 1)), 3);
    });

    test('plain text becomes unsynced lyrics', () {
      final l = Lyrics.parse('\nFirst line\nSecond line', 'embedded')!;
      expect(l.synced, isFalse);
      expect(l.lines.first.text, 'First line');
    });

    test('empty input yields null', () {
      expect(Lyrics.parse('   \n', 'file'), isNull);
    });
  });

  group('Smart lists', () {
    final now = DateTime.now().millisecondsSinceEpoch;
    final old = DateTime.now().subtract(const Duration(days: 60)).millisecondsSinceEpoch;
    final lib = [song(1), song(2, dateAdded: 300), song(3, dateAdded: 100), song(4, dateAdded: 200)];
    final stats = {
      1: (count: 2, lastPlayed: now),
      2: (count: 9, lastPlayed: old),
      3: (count: 1, lastPlayed: old - 1000),
    };

    test('most played sorts by count', () {
      expect(SmartList.mostPlayed.build(lib, stats).map((s) => s.id), [2, 1, 3]);
    });

    test('recently added sorts by date added', () {
      expect(SmartList.recentlyAdded.build(lib, stats).map((s) => s.id), [2, 4, 3]);
    });

    test('forgotten = played before, not in 30 days, oldest first', () {
      expect(SmartList.forgotten.build(lib, stats).map((s) => s.id), [3, 2]);
    });
  });

  group('Playlists', () {
    test('create, add (no duplicates), remove, restore, persist', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await PreferencesRepository.init();
      final provider = PlaylistProvider(prefs);
      final p = await provider.create('Road trip', songs: [song(1)]);
      expect(await provider.addSongs(p.id, [song(1), song(2)]), 1);
      expect(provider.byId(p.id)!.songIds, [1, 2]);
      await provider.removeSongAt(p.id, 0);
      expect(provider.byId(p.id)!.songIds, [2]);
      await provider.restoreIds(p.id, [1, 2]);
      expect(provider.songsOf(provider.byId(p.id)!, [song(2), song(1)]).map((s) => s.id), [1, 2]);

      // Reloads from preferences
      final reloaded = PlaylistProvider(prefs);
      expect(reloaded.playlists.single.name, 'Road trip');
      expect(reloaded.playlists.single.songIds, [1, 2]);
    });
  });

  group('Helpers', () {
    test('A–Z initials', () {
      expect(AlphabetScrollList.initialOf('aashish'), 'A');
      expect(AlphabetScrollList.initialOf('7 Years'), '#');
      expect(AlphabetScrollList.initialOf('青葉市子'), '#');
    });

    test('ordinals', () {
      expect([1, 2, 3, 4, 11, 12, 13, 21, 22].map(ordinal),
          ['1ST', '2ND', '3RD', '4TH', '11TH', '12TH', '13TH', '21ST', '22ND']);
    });

    test('play stats are recorded', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await PreferencesRepository.init();
      await prefs.recordPlay(7);
      await prefs.recordPlay(7);
      expect(prefs.getPlayStats()[7]!.count, 2);
    });
  });

  group('Queue order', () {
    final list = [for (var i = 1; i <= 8; i++) song(i)];

    test('chosen song first, the rest shuffled (all songs)', () {
      final q = PlayerProvider.queueOrder(list[5], list, source: 'All songs');
      expect(q.first.id, 6);
      expect(q.length, 8);
      expect(q.map((s) => s.id).toSet(), {1, 2, 3, 4, 5, 6, 7, 8});
    });

    test('albums keep order after the chosen song, wrapping round', () {
      final q = PlayerProvider.queueOrder(list[5], list, source: 'Album · Raazi');
      expect(q.map((s) => s.id), [6, 7, 8, 1, 2, 3, 4, 5]);
    });

    test('a song outside the list still plays first', () {
      final q = PlayerProvider.queueOrder(song(99), list, source: 'Search');
      expect(q.first.id, 99);
      expect(q.length, 9);
    });
  });

  group('Home picks', () {
    final lib = [for (var i = 1; i <= 40; i++) song(i)];
    final stats = {5: (count: 9, lastPlayed: 0), 7: (count: 3, lastPlayed: 0)};

    test('quick picks: 20 unique songs, recent first, stable within a day', () {
      final a = HomePicks.quickPicks(lib, [lib[30]], stats);
      final b = HomePicks.quickPicks(lib, [lib[30]], stats);
      expect(a.length, 20);
      expect(a.map((s) => s.path).toSet().length, 20);
      expect(a.first.id, 31);
      expect(a.map((s) => s.id), b.map((s) => s.id));
    });

    test('speed dial: most played first, filled to 18', () {
      final d = HomePicks.speedDial(lib, const [], stats);
      expect(d.take(2).map((s) => s.id), [5, 7]);
      expect(d.length, 18);
    });
  });
}
