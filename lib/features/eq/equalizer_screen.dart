import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' show PointMode;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_palette.dart';
import '../../core/theme/nothing_typography.dart';
import '../../shared/widgets/dot_matrix.dart';
import 'eq_model.dart';
import 'eq_provider.dart';
import 'iem_picker_screen.dart';

/// Equalizer: choose your IEM to apply its measured correction, then
/// fine-tune with a 10-band EQ. The graph shows exactly what's applied.
class EqualizerScreen extends StatelessWidget {
  const EqualizerScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final c = NothingPalette.of(context);
    final eq = context.watch<EqProvider>();
    final p = eq.selected;

    return Scaffold(
      backgroundColor: c.bg,
      appBar: AppBar(
        backgroundColor: c.bg,
        title: Text('EQUALIZER', style: NothingTypography.displayTitle(isDark: c.dark, fontSize: 18)),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: Switch(
              key: const Key('eq_enabled'),
              value: eq.enabled,
              activeThumbColor: NothingColors.darkDisplay,
              activeTrackColor: NothingColors.signalRed,
              onChanged: (v) {
                HapticFeedback.selectionClick();
                eq.setEnabled(v);
              },
            ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 40),
        children: [
          if (!eq.nativeAvailable)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Text(
                'This phone doesn\'t expose an audio equalizer to apps, so changes won\'t be heard.',
                style: TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 12, color: NothingColors.signalRedBright),
              ),
            ),

          // Your IEM
          Text('YOUR IEM', style: NothingTypography.monoLabel(isDark: c.dark)),
          const SizedBox(height: 8),
          Material(
            color: c.surface,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: BorderSide(color: c.line),
            ),
            child: InkWell(
              key: const Key('eq_choose_iem'),
              borderRadius: BorderRadius.circular(16),
              onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const IemPickerScreen())),
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Row(
                  children: [
                    DotArt(artId: p.measured ? 2 : 6, size: 46),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            p.title,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontFamily: NothingTypography.fontUi,
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                              color: c.display,
                            ),
                          ),
                          const SizedBox(height: 3),
                          Text(
                            p.measured
                                ? 'MEASURED BY ${p.source.toUpperCase()} · AUTOEQ'
                                : (p.filters.isEmpty ? 'NO CORRECTION' : 'SOUND PROFILE'),
                            style: TextStyle(
                              fontFamily: NothingTypography.fontMono,
                              fontSize: 10,
                              letterSpacing: 1,
                              color: c.secondary,
                            ),
                          ),
                          if (p.note != null) ...[
                            const SizedBox(height: 3),
                            Text(p.note!, style: TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 11, color: c.muted)),
                          ],
                        ],
                      ),
                    ),
                    Text(
                      'CHANGE',
                      style: TextStyle(
                        fontFamily: NothingTypography.fontMono,
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 1.2,
                        color: NothingColors.signalRedBright,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 22),

          // Response graph
          Row(
            children: [
              Expanded(child: Text('RESPONSE', style: NothingTypography.monoLabel(isDark: c.dark))),
              Text(
                'PREAMP ${eq.preamp.toStringAsFixed(1)} dB',
                style: TextStyle(fontFamily: NothingTypography.fontMono, fontSize: 10, letterSpacing: 1, color: c.secondary),
              ),
            ],
          ),
          const SizedBox(height: 8),
          AspectRatio(
            aspectRatio: 1.9,
            child: Opacity(
              opacity: eq.enabled ? 1 : 0.4,
              child: CustomPaint(
                painter: _ResponsePainter(
                  correction: p.filters,
                  manual: ManualBands.filters(eq.manual),
                  palette: c,
                ),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              _legend(c, NothingColors.signalRed, 'IEM correction'),
              const SizedBox(width: 14),
              _legend(c, c.secondary, 'Fine-tune'),
              const SizedBox(width: 14),
              _legend(c, c.display, 'Total'),
            ],
          ),
          const SizedBox(height: 26),

          // Fine-tune
          Row(
            children: [
              Expanded(child: Text('FINE-TUNE', style: NothingTypography.monoLabel(isDark: c.dark))),
              TextButton(
                key: const Key('eq_reset'),
                onPressed: eq.manual.every((g) => g == 0) ? null : eq.resetManual,
                child: Text('RESET', style: NothingTypography.monoLabel(isDark: c.dark, color: NothingColors.signalRedBright)),
              ),
            ],
          ),
          SizedBox(
            height: 210,
            child: Row(
              children: [
                for (var i = 0; i < ManualBands.freqs.length; i++)
                  Expanded(child: _BandSlider(index: i, value: eq.manual[i], palette: c)),
              ],
            ),
          ),
          const SizedBox(height: 18),
          Text(
            'Corrections from AutoEQ (github.com/jaakkopasanen/AutoEq, MIT), computed from the credited '
            'measurements towards the Harman in-ear target. Fit and ear tips change the sound, so use '
            'fine-tune to taste.',
            style: TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 11, height: 1.4, color: c.muted),
          ),
        ],
      ),
    );
  }

  Widget _legend(NothingPalette c, Color color, String label) => Row(
        children: [
          Container(width: 10, height: 3, color: color),
          const SizedBox(width: 6),
          Text(label, style: TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 11, color: c.secondary)),
        ],
      );
}

class _BandSlider extends StatelessWidget {
  final int index;
  final double value;
  final NothingPalette palette;
  const _BandSlider({required this.index, required this.value, required this.palette});

  @override
  Widget build(BuildContext context) {
    final c = palette;
    return Column(
      children: [
        Text(
          value == 0 ? '0' : '${value > 0 ? '+' : ''}${value.toStringAsFixed(value.abs() < 10 ? 1 : 0)}',
          style: TextStyle(
            fontFamily: NothingTypography.fontMono,
            fontSize: 9,
            color: value == 0 ? c.muted : c.display,
          ),
        ),
        Expanded(
          child: RotatedBox(
            quarterTurns: 3,
            child: SliderTheme(
              data: SliderTheme.of(context).copyWith(
                trackHeight: 2,
                // Neutral track (0 dB sits mid-way); the knob turns red when moved
                activeTrackColor: c.line2,
                inactiveTrackColor: c.line2,
                thumbColor: value == 0 ? c.display : NothingColors.signalRed,
                thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
                overlayShape: SliderComponentShape.noOverlay,
              ),
              child: Slider(
                key: Key('eq_band_$index'),
                min: -ManualBands.maxDb,
                max: ManualBands.maxDb,
                divisions: 48,
                value: value,
                onChanged: (v) {
                  if ((v * 2).round() != (value * 2).round()) HapticFeedback.selectionClick();
                  context.read<EqProvider>().setManual(index, v);
                },
              ),
            ),
          ),
        ),
        const SizedBox(height: 4),
        Text(
          ManualBands.labels[index],
          style: TextStyle(fontFamily: NothingTypography.fontMono, fontSize: 9, color: c.secondary),
        ),
      ],
    );
  }
}

/// Log-frequency response plot on a dot grid (±12 dB, 20 Hz–20 kHz).
class _ResponsePainter extends CustomPainter {
  final List<PeqFilter> correction;
  final List<PeqFilter> manual;
  final NothingPalette palette;

  _ResponsePainter({required this.correction, required this.manual, required this.palette});

  static const double _range = 12;

  @override
  void paint(Canvas canvas, Size size) {
    const left = 26.0, bottom = 16.0;
    final w = size.width - left, h = size.height - bottom;
    double x(double f) => left + w * math.log(f / 20) / math.log(1000);
    double y(double db) => h / 2 - (db.clamp(-_range, _range) / _range) * (h / 2);

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
  }

  @override
  bool shouldRepaint(_ResponsePainter old) =>
      old.correction != correction || old.palette != palette || !_same(old.manual, manual);

  static bool _same(List<PeqFilter> a, List<PeqFilter> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i].fc != b[i].fc || a[i].gain != b[i].gain) return false;
    }
    return true;
  }
}
