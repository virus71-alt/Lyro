import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lyro/core/theme/nothing_colors.dart';
import 'package:lyro/core/theme/nothing_theme.dart';
import 'package:lyro/core/utils/formatters.dart';
import 'package:lyro/data/models/album_model.dart';
import 'package:lyro/data/models/artist_model.dart';
import 'package:lyro/data/models/song_model.dart';
import 'package:lyro/features/player/widgets/lyro_speaker_player.dart';
import 'package:lyro/features/player/widgets/progress_bar.dart';
import 'package:lyro/shared/widgets/nothing_button.dart';
import 'package:lyro/shared/widgets/nothing_card.dart';
import 'package:lyro/shared/widgets/nothing_status_badge.dart';

void main() {
  group('Formatters & Utilities Tests', () {
    test('formatDuration formats correctly', () {
      expect(Formatters.formatDuration(Duration.zero), '00:00');
      expect(Formatters.formatDuration(const Duration(seconds: 45)), '00:45');
      expect(Formatters.formatDuration(const Duration(minutes: 3, seconds: 25)), '03:25');
      expect(Formatters.formatDuration(const Duration(hours: 1, minutes: 2, seconds: 5)), '1:02:05');
      expect(Formatters.formatDuration(null), '00:00');
    });

    test('formatCodec normalizes extensions correctly', () {
      expect(Formatters.formatCodec('flac', '/path/to/song.flac'), 'FLAC');
      expect(Formatters.formatCodec('.mp3', null), 'MP3');
      expect(Formatters.formatCodec(null, '/path/song.wav'), 'WAV');
      expect(Formatters.formatCodec(null, null), 'AUDIO');
    });

    test('formatTrackCount handles singular and plural', () {
      expect(Formatters.formatTrackCount(1), '1 TRACK');
      expect(Formatters.formatTrackCount(12), '12 TRACKS');
    });
  });

  group('Data Models Tests', () {
    test('Song model handles FLAC lossless recognition and conversion', () {
      const flacSong = Song(
        id: 101,
        title: 'Lossless Echoes',
        artist: 'Nothing Studio',
        album: 'Acoustic White',
        duration: 215000,
        path: '/storage/emulated/0/Music/Lossless Echoes.flac',
        format: 'FLAC',
      );

      expect(flacSong.isFlac, isTrue);
      expect(flacSong.formattedDuration, '03:35');

      final mediaItem = flacSong.toMediaItem();
      expect(mediaItem.id, flacSong.path);
      expect(mediaItem.title, 'Lossless Echoes');
      expect(mediaItem.artist, 'Nothing Studio');
      expect(mediaItem.album, 'Acoustic White');
      expect(mediaItem.extras?['isFlac'], isTrue);
    });

    test('Album and Artist model parsing', () {
      final album = Album.fromMap({
        'id': 1,
        'album': 'Design 01',
        'artist': 'Glyph',
        'numOfSongs': 10,
      });
      expect(album.album, 'Design 01');
      expect(album.numOfSongs, 10);

      final artist = Artist.fromMap({
        'id': 2,
        'artist': 'Glyph',
        'numberOfAlbums': 2,
        'numberOfTracks': 18,
      });
      expect(artist.artist, 'Glyph');
      expect(artist.numberOfTracks, 18);
    });
  });

  group('Nothing UI Theme & Widgets Tests', () {
    test('NothingColors dark and light theme tokens', () {
      expect(NothingColors.darkBg, const Color(0xFF000000));
      expect(NothingColors.darkSurface, const Color(0xFF0C0C0C));
      expect(NothingColors.darkDisplay, const Color(0xFFFFFFFF));
      expect(NothingColors.darkAccent, const Color(0xFFD71921));

      expect(NothingColors.lightBg, const Color(0xFFF2F2F2));
      expect(NothingColors.lightDisplay, const Color(0xFF000000));
    });

    testWidgets('NothingButton renders correctly in both variants', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: NothingTheme.darkTheme,
          home: Scaffold(
            body: Center(
              child: Column(
                children: [
                  NothingButton(
                    text: 'PLAY',
                    variant: NothingButtonVariant.primary,
                    onPressed: () {},
                  ),
                  NothingButton(
                    text: 'SHUFFLE',
                    variant: NothingButtonVariant.pill,
                    isSelected: true,
                    onPressed: () {},
                  ),
                ],
              ),
            ),
          ),
        ),
      );

      expect(find.text('PLAY'), findsOneWidget);
      expect(find.text('SHUFFLE'), findsOneWidget);
    });

    testWidgets('NothingCard renders frosted container', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: NothingTheme.darkTheme,
          home: const Scaffold(
            body: Center(
              child: NothingCard(
                child: Text('CARD CONTENT'),
              ),
            ),
          ),
        ),
      );

      expect(find.text('CARD CONTENT'), findsOneWidget);
    });

    testWidgets('NothingStatusBadge displays signal badge', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: NothingTheme.darkTheme,
          home: const Scaffold(
            body: Center(
              child: NothingStatusBadge(
                label: 'FLAC',
                type: BadgeType.flacHiRes,
                showDot: true,
              ),
            ),
          ),
        ),
      );

      expect(find.text('FLAC'), findsOneWidget);
    });

    testWidgets('LyroProgressBar renders and updates via ValueListenable', (tester) async {
      final posNotifier = ValueNotifier<Duration>(const Duration(seconds: 15));

      await tester.pumpWidget(
        MaterialApp(
          theme: NothingTheme.darkTheme,
          home: Scaffold(
            body: Center(
              child: LyroProgressBar(
                duration: const Duration(minutes: 3),
                positionListenable: posNotifier,
                onSeek: (_) {},
              ),
            ),
          ),
        ),
      );

      expect(find.text('00:15'), findsOneWidget);
      expect(find.text('03:00'), findsOneWidget);

      posNotifier.value = const Duration(seconds: 45);
      await tester.pump();

      expect(find.text('00:45'), findsOneWidget);
    });

    testWidgets('LyroSpeakerPlayer renders CMF speaker and knob interactions', (tester) async {
      bool playPauseCalled = false;
      bool shuffleCalled = false;

      final testSong = Song(
        id: 101,
        title: '7 Years',
        artist: 'Lukas Graham',
        album: 'Lukas Graham (Blue Album)',
        duration: 237000,
        path: '/storage/emulated/0/Music/7years.mp3',
        format: 'MP3',
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: NothingTheme.darkTheme,
          home: Scaffold(
            body: Center(
              child: LyroSpeakerPlayer(
                song: testSong,
                isPlaying: true,
                isShuffle: false,
                onPlayPause: () => playPauseCalled = true,
                onToggleShuffle: () => shuffleCalled = true,
                width: 280,
              ),
            ),
          ),
        ),
      );

      expect(find.byType(LyroSpeakerPlayer), findsOneWidget);
      expect(find.byKey(const Key('speaker_red_knob')), findsOneWidget);
      expect(find.byKey(const Key('speaker_shuffle_dial')), findsOneWidget);

      // Tap red play/pause knob
      await tester.tap(find.byKey(const Key('speaker_red_knob')));
      await tester.pump(const Duration(milliseconds: 50));
      expect(playPauseCalled, isTrue);

      // Tap secondary dial (shuffle control)
      await tester.tap(find.byKey(const Key('speaker_shuffle_dial')));
      await tester.pump(const Duration(milliseconds: 50));
      expect(shuffleCalled, isTrue);
    });
  });
}
