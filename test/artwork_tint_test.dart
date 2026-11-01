import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lyro/features/player/widgets/artwork_tint.dart';

void main() {
  testWidgets('ArtworkTintBuilder renders before any tint has loaded', (tester) async {
    Color? seen = const Color(0xFF123456);
    await tester.pumpWidget(
      ArtworkTintBuilder(
        songId: 0, // no artwork to load
        builder: (context, tint) {
          seen = tint;
          return const SizedBox();
        },
      ),
    );
    await tester.pump(const Duration(milliseconds: 600));
    expect(tester.takeException(), isNull);
    expect(seen, isNull);
  });

  test('background() falls back when there is no tint', () {
    expect(ArtworkTint.background(null), isA<Color>());
  });
}
