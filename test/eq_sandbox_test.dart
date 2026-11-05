import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:just_audio/just_audio.dart';
import 'package:lyro/core/theme/nothing_theme.dart';
import 'package:lyro/data/repositories/preferences_repository.dart';
import 'package:lyro/data/services/audio_handler.dart';
import 'package:lyro/features/eq/eq_graph.dart';
import 'package:lyro/features/eq/eq_model.dart';
import 'package:lyro/features/eq/eq_provider.dart';
import 'package:lyro/features/eq/eq_sandbox_screen.dart';
import 'package:lyro/features/eq/equalizer_screen.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Player implements AudioPlayer {
  @override
  Stream<int?> get androidAudioSessionIdStream => const Stream.empty();
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Handler implements LyroAudioHandler {
  @override
  final AudioPlayer player = _Player();
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<EqProvider> _provider() async =>
    EqProvider(prefsRepo: await PreferencesRepository.init(), audioHandler: _Handler());

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('graph scale converts both ways', () {
    final s = EqGraphScale(const Size(326, 216));
    expect(s.freqAt(s.x(1000)), closeTo(1000, 0.01));
    expect(s.dbAt(s.y(6)), closeTo(6, 0.01));
    expect(s.freqAt(-50), 20);
    expect(s.dbAt(-50), 12);
  });

  test('sandbox bands stay in range and join the applied curve', () async {
    final eq = await _provider();
    expect(eq.enabled, isFalse);
    final i = eq.addSandboxBand(fc: 999.6, gain: 6.04);
    expect(eq.enabled, isTrue); // first band is heard straight away
    expect(eq.sandbox[i].fc, 1000);
    expect(eq.sandbox[i].gain, 6);

    eq.updateSandboxBand(i, const PeqFilter('PK', 99999, 40, 99));
    expect(eq.sandbox[i].fc, SandboxBands.maxHz);
    expect(eq.sandbox[i].gain, 12);
    expect(eq.sandbox[i].q, SandboxBands.maxQ);

    eq.updateSandboxBand(i, const PeqFilter('PK', 1000, 6, 1));
    expect(EqCurve.responseDb(eq.allFilters, 1000), closeTo(6, 0.1));
    eq.removeSandboxBand(i);
    expect(eq.allFilters, isEmpty);
  });

  test('new bands land apart and stop at the limit', () async {
    final eq = await _provider();
    for (var i = 0; i < SandboxBands.max; i++) {
      eq.addSandboxBand();
    }
    expect(eq.canAddSandboxBand, isFalse);
    expect(eq.sandbox.map((b) => b.fc).toSet().length, greaterThan(5));
  });

  test('presets save the whole sound, reload it and survive a restart', () async {
    final eq = await _provider();
    await eq.setManual(2, 3);
    eq.addSandboxBand(fc: 2000, gain: -4);
    await eq.select(SoundProfiles.all[1]);
    final preset = eq.savePreset('Commute');
    expect(eq.activePreset?.id, preset.id);

    // Change everything; it's no longer that preset
    await eq.resetManual();
    eq.clearSandbox();
    await eq.select(SoundProfiles.all.first);
    expect(eq.activePreset, isNull);

    await eq.applyPreset(preset);
    expect(eq.selected.id, SoundProfiles.all[1].id);
    expect(eq.manual[2], 3);
    expect(eq.sandbox.single.fc, 2000);
    expect(eq.activePreset?.id, preset.id);

    eq.renamePreset(preset, 'Gym');
    eq.updateSandboxBand(0, eq.sandbox[0].copyWith(gain: 2));
    expect(eq.activePreset, isNull); // edited since it was saved
    eq.updatePreset(eq.presets.single);
    expect(eq.activePreset?.name, 'Gym');

    final again = await _provider();
    expect(again.presets.single.name, 'Gym');
    expect(again.sandbox.single.gain, 2);
    expect(again.activePreset?.name, 'Gym');

    again.deletePreset(again.presets.single);
    expect((await _provider()).presets, isEmpty);
  });

  Future<EqProvider> pump(WidgetTester tester, Widget screen) async {
    tester.view.physicalSize = const Size(360, 700);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final eq = await _provider();
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: eq,
        child: MaterialApp(theme: NothingTheme.darkTheme, home: screen),
      ),
    );
    await tester.pump();
    return eq;
  }

  testWidgets('tap the graph to add a band, drag it to move it, delete it', (tester) async {
    final eq = await pump(tester, const EqSandboxScreen());
    final graph = find.byKey(const Key('sandbox_graph'));
    final box = tester.getRect(graph);

    await tester.tapAt(box.center);
    await tester.pumpAndSettle();
    expect(eq.sandbox, hasLength(1));
    expect(find.byKey(const Key('sandbox_gain')), findsOneWidget); // editor opens
    expect(tester.takeException(), isNull);

    final before = eq.sandbox.single;
    final scale = EqGraphScale(box.size);
    final node = box.topLeft + scale.point(before.fc, before.gain);
    await tester.timedDragFrom(node, const Offset(60, -50), const Duration(milliseconds: 300));
    await tester.pumpAndSettle();
    expect(eq.sandbox, hasLength(1));
    expect(eq.sandbox.single.fc, greaterThan(before.fc));
    expect(eq.sandbox.single.gain, greaterThan(before.gain));

    await tester.tap(find.byKey(const Key('sandbox_type_HS')));
    await tester.pump();
    expect(eq.sandbox.single.type, 'HS');

    await tester.tap(find.byKey(const Key('sandbox_delete')));
    await tester.pumpAndSettle();
    expect(eq.sandbox, isEmpty);
  });

  testWidgets('save a preset from the Equalizer screen and load it again', (tester) async {
    final eq = await pump(tester, const EqualizerScreen());
    await tester.tap(find.byKey(const Key('eq_preset_save')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('playlist_name_field')), 'Night');
    await tester.tap(find.byKey(const Key('playlist_name_ok')));
    await tester.pumpAndSettle();
    expect(eq.presets.single.name, 'Night');
    expect(find.text('NIGHT'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
