import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' show PointMode;

import 'package:flutter/material.dart';

import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_palette.dart';
import '../../core/theme/nothing_typography.dart';
import 'eq_model.dart';

/// Maps between graph pixels and frequency / gain.
class EqGraphScale {
  static const double left = 26, bottom = 16, range = 12;

  final double w, h;
  EqGraphScale(Size size)
      : w = size.width - left,
        h = size.height - bottom;

  double x(double f) => left + w * math.log(f / 20) / math.log(1000);
  double y(double db) => h / 2 - (db.clamp(-range, range) / range) * (h / 2);
  Offset point(double f, double db) => Offset(x(f), y(db));

  double freqAt(double px) => 20 * math.pow(1000, ((px - left) / w).clamp(0.0, 1.0)).toDouble();
  double dbAt(double px) => ((h / 2 - px) / (h / 2) * range).clamp(-range, range);
}

/// Log-frequency response plot on a dot grid (±12 dB, 20 Hz–20 kHz), with the
/// Sandbox's draggable bands as numbered [nodes].
class EqResponsePainter extends CustomPainter {
  final List<PeqFilter> correction;
  final List<PeqFilter> manual;
  final NothingPalette palette;
  final List<PeqFilter> nodes;
  final int? selectedNode;

  EqResponsePainter({
    required this.correction,
    required this.manual,
    required this.palette,
    this.nodes = const [],
    this.selectedNode,
  });

  @override
  void paint(Canvas canvas, Size size) {
    const left = EqGraphScale.left;
    final scale = EqGraphScale(size);
    final w = scale.w, h = scale.h;
    final x = scale.x, y = scale.y;

    // Dot grid
    final dots = <double>[];
    for (var gx = 0; gx <= 40; gx++) {
      for (var gy = 0; gy <= 12; gy++) {
        dots..add(left + w * gx / 40)..add(h * gy / 12);
      }
    }
    canvas.drawRawPoints(
      PointMode.points,
      Float32List.fromList(dots),
      Paint()
        ..color = palette.line2
        ..strokeWidth = 1.6
        ..strokeCap = StrokeCap.round,
    );
    // 0 dB line
    canvas.drawLine(Offset(left, y(0)), Offset(size.width, y(0)), Paint()..color = palette.line2..strokeWidth = 1);

    // Labels
    void label(String t, Offset o) {
      final tp = TextPainter(
        text: TextSpan(text: t, style: TextStyle(fontFamily: NothingTypography.fontMono, fontSize: 8.5, color: palette.muted)),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, o);
    }

    for (final (db, t) in [(12.0, '+12'), (0.0, '0'), (-12.0, '-12')]) {
      label(t, Offset(0, y(db) - 5));
    }
    for (final (f, t) in [(20.0, '20'), (100.0, '100'), (1000.0, '1K'), (10000.0, '10K'), (20000.0, '20K')]) {
      label(t, Offset((x(f) - 8).clamp(left, size.width - 18), h + 3));
    }

    Path curve(List<PeqFilter> filters) {
      final path = Path();
      for (var i = 0; i <= 160; i++) {
        final f = 20 * math.pow(1000, i / 160).toDouble();
        final pt = Offset(x(f), y(EqCurve.responseDb(filters, f)));
        i == 0 ? path.moveTo(pt.dx, pt.dy) : path.lineTo(pt.dx, pt.dy);
      }
      return path;
    }

    Paint stroke(Color c, double width) => Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = width
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..color = c;

    if (manual.isNotEmpty) canvas.drawPath(curve(manual), stroke(palette.secondary, 1.5));
    if (correction.isNotEmpty) canvas.drawPath(curve(correction), stroke(NothingColors.signalRed, 2));
    canvas.drawPath(curve([...correction, ...manual]), stroke(palette.display, 2.6));

    for (final (i, node) in nodes.indexed) {
      final c = scale.point(node.fc, node.gain);
      final on = i == selectedNode;
      canvas.drawCircle(c, 11, Paint()..color = on ? NothingColors.signalRed : palette.bg);
      canvas.drawCircle(c, 11, stroke(on ? NothingColors.signalRed : palette.display, 1.5));
      final tp = TextPainter(
        text: TextSpan(
          text: '${i + 1}',
          style: TextStyle(
            fontFamily: NothingTypography.fontMono,
            fontSize: 10,
            fontWeight: FontWeight.w600,
            color: on ? Colors.white : palette.display,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, c - Offset(tp.width / 2, tp.height / 2));
    }
  }

  @override
  bool shouldRepaint(EqResponsePainter old) =>
      old.correction != correction ||
      old.palette != palette ||
      old.selectedNode != selectedNode ||
      !_same(old.manual, manual) ||
      !_same(old.nodes, nodes);

  static bool _same(List<PeqFilter> a, List<PeqFilter> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (!a[i].sameAs(b[i])) return false;
    }
    return true;
  }
}
