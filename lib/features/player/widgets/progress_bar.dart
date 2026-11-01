import 'dart:ui' show PointMode;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../core/utils/formatters.dart';

class LyroProgressBar extends StatefulWidget {
  final Duration position;
  final Duration duration;
  final Duration bufferedPosition;
  final ValueListenable<Duration>? positionListenable;
  final ValueListenable<Duration>? bufferedPositionListenable;
  final ValueChanged<Duration> onSeek;

  const LyroProgressBar({
    super.key,
    this.position = Duration.zero,
    required this.duration,
    this.bufferedPosition = Duration.zero,
    this.positionListenable,
    this.bufferedPositionListenable,
    required this.onSeek,
  });

  @override
  State<LyroProgressBar> createState() => _LyroProgressBarState();
}

class _LyroProgressBarState extends State<LyroProgressBar> {
  double? _dragValue;
  int _lastDecile = -1;

  /// Haptic detents while scrubbing: a tick every 10 %, firmer at the ends.
  void _scrub(double ms, double maxMs) {
    if (maxMs > 0) {
      final f = ms / maxMs;
      final decile = (f * 10).floor().clamp(0, 10);
      if (decile != _lastDecile) {
        if (_lastDecile != -1) {
          (decile == 0 || decile == 10)
              ? HapticFeedback.mediumImpact()
              : HapticFeedback.selectionClick();
        }
        _lastDecile = decile;
      }
    }
    setState(() => _dragValue = ms);
  }

  void _commitSeek() {
    final value = _dragValue;
    if (value == null) return;
    HapticFeedback.selectionClick();
    widget.onSeek(Duration(milliseconds: value.toInt()));
    setState(() => _dragValue = null);
  }

  @override
  Widget build(BuildContext context) {
    if (widget.positionListenable != null) {
      return ValueListenableBuilder<Duration>(
        valueListenable: widget.positionListenable!,
        builder: (context, currentPos, _) {
          return _buildSlider(context, currentPos);
        },
      );
    }
    return _buildSlider(context, widget.position);
  }

  Widget _buildSlider(BuildContext context, Duration currentPos) {
    final isDark = context.isDark;
    final maxMs = widget.duration.inMilliseconds.toDouble();
    final currentMs = _dragValue ?? currentPos.inMilliseconds.toDouble().clamp(0.0, maxMs > 0 ? maxMs : 1.0);
    final displayPosition = _dragValue != null
        ? Duration(milliseconds: _dragValue!.toInt())
        : currentPos;

    final fraction = maxMs > 0 ? (currentMs / maxMs).clamp(0.0, 1.0) : 0.0;

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        // Nothing-style dot timeline: tap or drag anywhere to seek
        LayoutBuilder(
          builder: (context, constraints) {
            final width = constraints.maxWidth - 32;
            double toMs(double dx) =>
                ((dx - 16) / width).clamp(0.0, 1.0) * (maxMs > 0 ? maxMs : 0);
            return GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTapDown: (d) => setState(() => _dragValue = toMs(d.localPosition.dx)),
              onTapUp: (_) => _commitSeek(),
              onTapCancel: () => setState(() => _dragValue = null),
              onHorizontalDragStart: (d) {
                _lastDecile = -1;
                _scrub(toMs(d.localPosition.dx), maxMs);
              },
              onHorizontalDragUpdate: (d) => _scrub(toMs(d.localPosition.dx), maxMs),
              onHorizontalDragEnd: (_) => _commitSeek(),
              child: SizedBox(
                height: 32,
                width: double.infinity,
                child: RepaintBoundary(
                  child: CustomPaint(
                    painter: _DotTimelinePainter(
                      fraction: fraction,
                      dragging: _dragValue != null,
                      isDark: isDark,
                    ),
                  ),
                ),
              ),
            );
          },
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16.0),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                Formatters.formatDuration(displayPosition),
                style: TextStyle(
                  fontFamily: NothingTypography.fontMono,
                  fontSize: 11,
                  letterSpacing: 0.8,
                  color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                ),
              ),
              Text(
                Formatters.formatDuration(widget.duration),
                style: TextStyle(
                  fontFamily: NothingTypography.fontMono,
                  fontSize: 11,
                  letterSpacing: 0.8,
                  color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// Row of dots: played dots in signal red (brightest just behind the head),
/// upcoming dots in grey, and a white glyph bar as the playhead.
class _DotTimelinePainter extends CustomPainter {
  final double fraction;
  final bool dragging;
  final bool isDark;

  _DotTimelinePainter({
    required this.fraction,
    required this.dragging,
    required this.isDark,
  });

  static const double _pitch = 7.0;
  static const double _dot = 2.6;

  @override
  void paint(Canvas canvas, Size size) {
    const inset = 16.0;
    final width = size.width - inset * 2;
    final count = (width / _pitch).floor() + 1;
    final step = width / (count - 1);
    final cy = size.height / 2;
    final headX = inset + width * fraction;

    final played = <double>[], trail = <double>[], ahead = <double>[];
    for (var i = 0; i < count; i++) {
      final x = inset + i * step;
      if (x < headX - 6) {
        (x > headX - step * 6 ? trail : played)..add(x)..add(cy);
      } else if (x > headX + 6) {
        ahead..add(x)..add(cy);
      }
    }
    void dots(List<double> pts, Color color) {
      if (pts.isEmpty) return;
      canvas.drawRawPoints(
        PointMode.points,
        Float32List.fromList(pts),
        Paint()
          ..color = color
          ..strokeWidth = _dot
          ..strokeCap = StrokeCap.round,
      );
    }

    dots(ahead, isDark ? NothingColors.darkLine2 : NothingColors.lightLine2);
    dots(played, NothingColors.signalRed.withValues(alpha: 0.75));
    dots(trail, NothingColors.signalRedBright);

    // Playhead glyph bar
    final h = dragging ? 20.0 : 14.0;
    final w = dragging ? 4.0 : 3.0;
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromCenter(center: Offset(headX, cy), width: w, height: h),
        Radius.circular(w / 2),
      ),
      Paint()..color = isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
    );
  }

  @override
  bool shouldRepaint(_DotTimelinePainter old) =>
      old.fraction != fraction || old.dragging != dragging || old.isDark != isDark;
}
