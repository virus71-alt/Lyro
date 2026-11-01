import 'dart:typed_data';
import 'dart:ui' show PointMode;

import 'package:flutter/material.dart';
import 'nothing_colors.dart';

/// Renders the signature Nothing UI dot-field background.
/// Every dot is a crisp complete circle spaced regularly across the canvas.
class NothingDotPainter extends CustomPainter {
  final bool isDark;
  final double spacing;
  final double dotRadius;

  NothingDotPainter({
    required this.isDark,
    this.spacing = 24.0,
    this.dotRadius = 1.0,
  });

  @override
  void paint(Canvas canvas, Size size) {
    // One batched points draw instead of a drawCircle per dot: the field has
    // thousands of dots and is re-rasterised every frame behind animations.
    final cols = (size.width / spacing).ceil();
    final rows = (size.height / spacing).ceil();
    final points = Float32List(cols * rows * 2);
    var i = 0;
    for (var cx = 0; cx < cols; cx++) {
      final x = spacing / 2 + cx * spacing;
      for (var cy = 0; cy < rows; cy++) {
        points[i++] = x;
        points[i++] = spacing / 2 + cy * spacing;
      }
    }
    canvas.drawRawPoints(
      PointMode.points,
      points,
      Paint()
        ..color = isDark ? NothingColors.darkDot : NothingColors.lightDot
        ..strokeWidth = dotRadius * 2
        ..strokeCap = StrokeCap.round,
    );
  }

  @override
  bool shouldRepaint(covariant NothingDotPainter oldDelegate) {
    return oldDelegate.isDark != isDark ||
        oldDelegate.spacing != spacing ||
        oldDelegate.dotRadius != dotRadius;
  }
}

