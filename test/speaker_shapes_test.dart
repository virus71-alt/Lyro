import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lyro/core/settings/app_settings_provider.dart';
import 'package:lyro/core/theme/nothing_theme.dart';
import 'package:lyro/data/models/song_model.dart';
import 'package:lyro/data/repositories/preferences_repository.dart';
import 'package:lyro/data/services/audio_handler.dart';
import 'package:lyro/features/player/widgets/cmf_speaker_3d.dart';
import 'package:lyro/features/player/widgets/lyro_speaker_player.dart';
import 'package:lyro/features/player/widgets/speaker_preview.dart';
import 'package:lyro/features/profile/widgets/speaker_style_card.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _SilentHandler implements LyroAudioHandler {
  @override
  Duration fade = Duration.zero;
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  test('every profile is convex and counter-clockwise', () {
    for (final shape in SpeakerShape.values) {
      final p = shape.profile;
      for (var i = 0; i < p.length; i++) {
        final a = p[i], b = p[(i + 1) % p.length], c = p[(i + 2) % p.length];
        final turn = (b.dx - a.dx) * (c.dy - b.dy) - (b.dy - a.dy) * (c.dx - b.dx);
        expect(turn, greaterThan(0), reason: '${shape.label} corner $i');
      }
    }
  });

  test('controls and artwork fit on each body', () {
    for (final shape in SpeakerShape.values) {
      expect(shape.knobX.abs() + 0.105 * shape.knobScale, lessThanOrEqualTo(shape.width / 2), reason: shape.label);
      expect(shape.dialX.abs() + 0.112 * shape.dialScale, lessThanOrEqualTo(shape.width / 2), reason: shape.label);
      expect(shape.apertureX.abs() + shape.apertureRadius, lessThanOrEqualTo(shape.width / 2), reason: shape.label);
      expect(shape.apertureY + shape.apertureRadius, lessThanOrEqualTo(shape.wallHeight), reason: shape.label);
      expect(shape.apertureY - shape.apertureRadius, greaterThanOrEqualTo(0), reason: shape.label);
      expect(shape.cornerRadius, greaterThanOrEqualTo(shape.bevel), reason: shape.label);
    }
  });

  testWidgets('all models paint from several painters at once', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Wrap(
          children: [
            for (final shape in SpeakerShape.values)
              SpeakerPreview(
                shape: shape,
                body: SpeakerGeometry.bodyColor,
                knob: SpeakerGeometry.knobColor,
                dial: SpeakerGeometry.dialColor,
                logoColor: Colors.white,
                width: 90,
                height: 120,
              ),
          ],
        ),
      ),
    );
    expect(tester.takeException(), isNull);
    expect(find.byType(SpeakerPreview), findsNWidgets(SpeakerShape.values.length));
  });

  for (final shape in SpeakerShape.values) {
    testWidgets('${shape.label}: knob and dial work where they are drawn', (tester) async {
      var played = 0, dialed = 0;
      const song = Song(id: 1, title: 'Song', artist: 'Artist', album: 'Album', duration: 1000, path: '/a.mp3', format: 'MP3');
      await tester.pumpWidget(
        MaterialApp(
          theme: NothingTheme.darkTheme,
          home: Scaffold(
            body: Center(
              child: LyroSpeakerPlayer(
                song: song,
                shape: shape,
                isPlaying: false,
                onPlayPause: () => played++,
                onDialTap: () => dialed++,
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.byKey(const Key('speaker_red_knob')));
      await tester.pump(const Duration(milliseconds: 300));
      await tester.tap(find.byKey(const Key('speaker_sleep_dial')));
      await tester.pump(const Duration(milliseconds: 300));
      expect(tester.takeException(), isNull);
      expect((played, dialed), (1, 1));
    });
  }

  testWidgets('Profile picker selects a model and remembers it', (tester) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    SharedPreferences.setMockInitialValues({});
    final prefs = await PreferencesRepository.init();
    final settings = AppSettingsProvider(prefsRepo: prefs, audioHandler: _SilentHandler());
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: settings,
        child: MaterialApp(
          theme: NothingTheme.darkTheme,
          home: const Scaffold(
            body: Padding(padding: EdgeInsets.all(16), child: SpeakerStyleCard()),
          ),
        ),
      ),
    );
    expect(tester.takeException(), isNull); // no overflow on a narrow phone
    expect(settings.speakerShape, SpeakerShape.classic);

    await tester.tap(find.byKey(const Key('speaker_style_cube')));
    await tester.pump();
    expect(settings.speakerShape, SpeakerShape.cube);
    expect(AppSettingsProvider(prefsRepo: prefs, audioHandler: _SilentHandler()).speakerShape, SpeakerShape.cube);
  });
}
