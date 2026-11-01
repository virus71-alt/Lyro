import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' show PointMode;

import 'package:flutter/material.dart';

/// Custom painter for the stationary metallic speaker grille and convex speaker cone.
///
/// Implements a physically plausible speaker cone:
/// 1. Subtle convex cone illumination: directional ambient light and gentle radial dome shading
///    making the central artwork appear slightly raised toward the viewer.
/// 2. Thin, subtle outer edge transition into the metallic bezel without any deep black tunnel.
/// 3. Stationary micro-perforated metallic grille mesh with high transparency so album artwork
///    remains vibrant, sharp, and clearly visible.
class SpeakerGrillePainter extends CustomPainter {
  final bool isDark;
  final double opacity;

  SpeakerGrillePainter({
    required this.isDark,
    this.opacity = 0.24,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2;

    // -------------------------------------------------------------------------
    // 1. Convex Speaker Cone Illumination
    // Subtle ambient lighting gives the speaker cone a realistic outward curvature:
    // - Gentle highlight along the upper-left edge
    // - Soft ambient shadow along the lower-right edge
    // - Subtle brighter center dome without any dark inward radial tunnel
    // -------------------------------------------------------------------------

    // A. Directional Light Across the Convex Dome
    final directionalShadingPaint = Paint()
      ..shader = LinearGradient(
        begin: const Alignment(-0.35, -0.90),
        end: const Alignment(0.35, 0.90),
        colors: [
          Colors.white.withValues(alpha: 0.12), // subtle upper-edge highlight
          Colors.white.withValues(alpha: 0.03), // central illumination
          Colors.transparent,
          Colors.black.withValues(alpha: 0.14), // soft lower-edge shadow
        ],
        stops: const [0.0, 0.35, 0.70, 1.0],
      ).createShader(Rect.fromCircle(center: center, radius: radius));

    canvas.drawCircle(center, radius, directionalShadingPaint);

    // B. Gentle Radial Convex Illumination (brighter center, subtle outer transition)
    final radialConvexPaint = Paint()
      ..shader = RadialGradient(
        colors: [
          Colors.white.withValues(alpha: 0.08), // raised center dome catch
          Colors.transparent,
          Colors.black.withValues(alpha: 0.18), // soft transition into thin rim
        ],
        stops: const [0.0, 0.65, 1.0],
      ).createShader(Rect.fromCircle(center: center, radius: radius));

    canvas.drawCircle(center, radius, radialConvexPaint);

    // -------------------------------------------------------------------------
    // 2. Stationary Micro-Perforated Grille Mesh
    // Hexagonal staggered micro-perforations simulating physical CMF laser-punched mesh
    // -------------------------------------------------------------------------
    final holeRadius = math.max(0.95, size.width * 0.0095);
    final step = holeRadius * 4.0;

    // Perforations as two batched point draws (rim catch, then hole) rather
    // than two drawCircle calls per hole.
    final holes = <double>[];
    final rims = <double>[];
    int rowIndex = 0;
    for (double y = 0; y <= size.height; y += step * 0.866) {
      final xOffset = (rowIndex % 2 == 0) ? 0.0 : step * 0.5;
      for (double x = -step; x <= size.width + step; x += step) {
        final dotCenter = Offset(x + xOffset, y);
        if ((dotCenter - center).distance <= radius - 2.5) {
          holes..add(dotCenter.dx)..add(dotCenter.dy);
          rims..add(dotCenter.dx)..add(dotCenter.dy + 0.5);
        }
      }
      rowIndex++;
    }

    canvas.drawRawPoints(
      PointMode.points,
      Float32List.fromList(rims),
      Paint()
        ..strokeWidth = holeRadius * 2 + 0.6
        ..strokeCap = StrokeCap.round
        ..color = Colors.white.withValues(alpha: opacity * 0.35),
    );
    canvas.drawRawPoints(
      PointMode.points,
      Float32List.fromList(holes),
      Paint()
        ..strokeWidth = holeRadius * 2
        ..strokeCap = StrokeCap.round
        ..color = Colors.black.withValues(alpha: opacity),
    );
  }

  @override
  bool shouldRepaint(covariant SpeakerGrillePainter oldDelegate) {
    return oldDelegate.isDark != isDark || oldDelegate.opacity != opacity;
  }
}
