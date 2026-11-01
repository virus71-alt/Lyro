import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:lyro/features/eq/eq_model.dart';

void main() {
  group('Filter maths', () {
    test('peaking filter reaches its gain at fc and ~0 dB far away', () {
      const f = PeqFilter('PK', 1000, 6, 1.0);
      expect(f.responseDb(1000), closeTo(6, 0.05));
      expect(f.responseDb(20), closeTo(0, 0.1));
      expect(f.responseDb(15000), closeTo(0, 0.2));
    });

    test('low shelf boosts the bass, leaves the treble', () {
      const f = PeqFilter('LS', 105, 5, 0.7);
      expect(f.responseDb(20), closeTo(5, 0.4));
      expect(f.responseDb(5000), closeTo(0, 0.1));
    });

    test('high shelf cuts the treble, leaves the bass', () {
      const f = PeqFilter('HS', 8000, -4, 0.7);
      expect(f.responseDb(20000), closeTo(-4, 0.5));
      expect(f.responseDb(100), closeTo(0, 0.05));
    });

    test('filters add in dB', () {
      const a = PeqFilter('PK', 1000, 3, 1.0), b = PeqFilter('PK', 1000, 2, 1.0);
      expect(EqCurve.responseDb([a, b], 1000), closeTo(5, 0.05));
    });

    test('32 log-spaced bands from 20 Hz to 20 kHz', () {
      expect(EqCurve.bandEdges.length, 32);
      expect(EqCurve.bandEdges.last, closeTo(20000, 1));
      expect(EqCurve.bandCenters.first, greaterThan(20));
    });

    test('preamp gives headroom for the largest boost', () {
      expect(EqCurve.preampFor([-3, 4.2, 1]), -4.2);
      expect(EqCurve.preampFor([-3, -1]), 0);
    });
  });

  test('parses AutoEQ ParametricEQ.txt', () {
    const text = 'Preamp: -4.2 dB\n'
        'Filter 1: ON LSC Fc 105 Hz Gain 3.8 dB Q 0.70\n'
        'Filter 2: ON PK Fc 2100 Hz Gain -2.5 dB Q 1.20\n'
        'Filter 3: ON HSC Fc 10000 Hz Gain 1.5 dB Q 0.70\n';
    final p = IemProfile.parseAutoEq(text, id: 'x', brand: 'B', name: 'N', source: 'S')!;
    expect(p.preamp, -4.2);
    expect(p.filters.map((f) => f.type), ['LS', 'PK', 'HS']);
    expect(p.filters[1].fc, 2100);
    expect(p.filters[1].q, 1.2);
  });

  test('bundled IEM profiles load and stay within sane limits', () {
    final j = jsonDecode(File('assets/eq/iem_profiles.json').readAsStringSync()) as Map<String, dynamic>;
    final profiles = [for (final p in j['profiles'] as List) IemProfile.fromJson(p as Map<String, dynamic>)];
    expect(profiles.length, greaterThanOrEqualTo(14));
    expect(profiles.any((p) => p.name.contains("Wan'er S.G 2")), isTrue);
    for (final p in profiles) {
      expect(p.filters, isNotEmpty, reason: p.title);
      final gains = EqCurve.bandGains(p.filters);
      expect(gains.every((g) => g.abs() < 20), isTrue, reason: p.title);
      expect(EqCurve.preampFor(gains), lessThanOrEqualTo(0));
    }
  });

  test('manual bands only add filters for non-zero gains', () {
    final gains = List<double>.filled(10, 0)..[3] = 4;
    final f = ManualBands.filters(gains);
    expect(f.length, 1);
    expect(f.single.fc, 250);
  });
}
