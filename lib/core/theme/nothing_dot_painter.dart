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
    final paint = Paint()
      ..color = isDark ? NothingColors.darkDot : NothingColors.lightDot
      ..style = PaintingStyle.fill
      ..isAntiAlias = true;

    for (double x = spacing / 2; x < size.width; x += spacing) {
      for (double y = spacing / 2; y < size.height; y += spacing) {
        canvas.drawCircle(Offset(x, y), dotRadius, paint);
      }
    }
  }

  @override
  bool shouldRepaint(covariant NothingDotPainter oldDelegate) {
    return oldDelegate.isDark != isDark ||
        oldDelegate.spacing != spacing ||
        oldDelegate.dotRadius != dotRadius;
  }
}

