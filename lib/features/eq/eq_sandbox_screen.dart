import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_palette.dart';
import '../../core/theme/nothing_typography.dart';
import 'eq_graph.dart';
import 'eq_model.dart';
import 'eq_provider.dart';

/// Sandbox: a free parametric EQ. Tap the graph to drop a band, drag it to set
/// frequency and gain, then pick its type and width below.
class EqSandboxScreen extends StatefulWidget {
  const EqSandboxScreen({super.key});

  @override
  State<EqSandboxScreen> createState() => _EqSandboxScreenState();
}

class _EqSandboxScreenState extends State<EqSandboxScreen> {
  static const _grabRadius = 30.0;

  int? _selected;
  int? _dragging;

  EqProvider get _eq => context.read<EqProvider>();

  int? _bandAt(Offset local, Size size) {
    final scale = EqGraphScale(size);
    int? best;
    var bestDistance = _grabRadius;
    for (final (i, band) in _eq.sandbox.indexed) {
      final d = (scale.point(band.fc, band.gain) - local).distance;
      if (d < bestDistance) {
        best = i;
        bestDistance = d;
      }
    }
    return best;
  }

  void _tap(Offset local, Size size) {
    final hit = _bandAt(local, size);
    if (hit != null) {
      setState(() => _selected = hit);
      return;
    }
    if (!_eq.canAddSandboxBand) return;
    final scale = EqGraphScale(size);
    HapticFeedback.selectionClick();
    final added = _eq.addSandboxBand(fc: scale.freqAt(local.dx), gain: scale.dbAt(local.dy));
    setState(() => _selected = added);
  }

  void _dragStart(Offset local, Size size) {
    final hit = _bandAt(local, size);
    if (hit == null) return;
    setState(() {
      _dragging = hit;
      _selected = hit;
    });
  }

  void _dragUpdate(Offset local, Size size) {
    final i = _dragging;
    if (i == null) return;
    final scale = EqGraphScale(size);
    _eq.updateSandboxBand(
      i,
      _eq.sandbox[i].copyWith(fc: scale.freqAt(local.dx), gain: scale.dbAt(local.dy)),
    );
  }

  static String _hz(double f) => f >= 1000
      ? '${(f / 1000).toStringAsFixed(f >= 10000 ? 1 : 2)} kHz'
      : '${f.round()} Hz';

  @override
  Widget build(BuildContext context) {
    final c = NothingPalette.of(context);
    final eq = context.watch<EqProvider>();
    final bands = eq.sandbox;
    final selected = _selected != null && _selected! < bands.length ? _selected : null;

    return Scaffold(
      backgroundColor: c.bg,
      appBar: AppBar(
        backgroundColor: c.bg,
        title: Text('SANDBOX', style: NothingTypography.displayTitle(isDark: c.dark, fontSize: 18)),
        actions: [
          TextButton(
            key: const Key('sandbox_clear'),
            onPressed: bands.isEmpty
                ? null
                : () {
                    setState(() => _selected = null);
                    eq.clearSandbox();
                  },
            child: Text('CLEAR', style: NothingTypography.monoLabel(isDark: c.dark, color: NothingColors.signalRedBright)),
          ),
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: Switch(
              key: const Key('sandbox_enabled'),
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
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 4, 20, 8),
            child: AspectRatio(
              aspectRatio: 1.45,
              child: LayoutBuilder(
                builder: (context, box) {
                  final size = box.biggest;
                  return GestureDetector(
                    key: const Key('sandbox_graph'),
                    behavior: HitTestBehavior.opaque,
                    onTapUp: (d) => _tap(d.localPosition, size),
                    onPanStart: (d) => _dragStart(d.localPosition, size),
                    onPanUpdate: (d) => _dragUpdate(d.localPosition, size),
                    onPanEnd: (_) => _dragging = null,
                    child: Opacity(
                      opacity: eq.enabled ? 1 : 0.4,
                      child: CustomPaint(
                        size: size,
                        painter: EqResponsePainter(
                          correction: eq.selected.filters,
                          manual: [...ManualBands.filters(eq.manual), ...bands],
                          palette: c,
                          nodes: bands,
                          selectedNode: selected,
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Text(
              bands.isEmpty
                  ? 'TAP THE GRAPH TO ADD A BAND'
                  : '${bands.length} OF ${SandboxBands.max} BANDS  ·  PREAMP ${eq.preamp.toStringAsFixed(1)} dB',
              style: NothingTypography.monoLabel(isDark: c.dark),
            ),
          ),
          const SizedBox(height: 10),
          SizedBox(
            height: 38,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 20),
              children: [
                for (var i = 0; i < bands.length; i++)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: _Pill(
                      key: Key('sandbox_band_$i'),
                      label: '${i + 1}  ${_hz(bands[i].fc)}',
                      selected: i == selected,
                      palette: c,
                      onTap: () => setState(() => _selected = i),
                    ),
                  ),
                if (eq.canAddSandboxBand)
                  _Pill(
                    key: const Key('sandbox_add'),
                    label: '+ ADD',
                    selected: false,
                    palette: c,
                    onTap: () {
                      HapticFeedback.selectionClick();
                      final added = eq.addSandboxBand();
                      setState(() => _selected = added);
                    },
                  ),
              ],
            ),
          ),
          Expanded(
            child: selected == null
                ? _hint(c)
                : _BandEditor(
                    key: ValueKey('band_editor_$selected'),
                    index: selected,
                    band: bands[selected],
                    palette: c,
                    hz: _hz,
                    onDelete: () {
                      setState(() => _selected = null);
                      eq.removeSandboxBand(selected);
                    },
                  ),
          ),
        ],
      ),
    );
  }

  Widget _hint(NothingPalette c) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 20),
        child: Text(
          'Each band adds on top of your IEM correction and fine-tune. Save the sound you '
          'like as a preset on the Equalizer screen. Very narrow bands are smoothed by the '
          '32-band engine.',
          style: TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 12, height: 1.4, color: c.muted),
        ),
      );
}

class _Pill extends StatelessWidget {
  final String label;
  final bool selected;
  final NothingPalette palette;
  final VoidCallback onTap;

  /// Shrinks the label to fit a shared row.
  final bool compact;

  const _Pill({
    super.key,
    required this.label,
    required this.selected,
    required this.palette,
    required this.onTap,
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    final c = palette;
    final text = Text(
      label,
      maxLines: 1,
      style: TextStyle(
        fontFamily: NothingTypography.fontMono,
        fontSize: 11,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.6,
        color: selected ? Colors.white : c.display,
      ),
    );
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: Container(
        padding: EdgeInsets.symmetric(horizontal: compact ? 8 : 14),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: selected ? NothingColors.signalRed : null,
          borderRadius: BorderRadius.circular(NothingColors.radiusPill),
          border: Border.all(color: selected ? NothingColors.signalRed : c.line2),
        ),
        child: compact ? FittedBox(fit: BoxFit.scaleDown, child: text) : text,
      ),
    );
  }
}

class _BandEditor extends StatelessWidget {
  static const _types = [('PK', 'BELL'), ('LS', 'LOW SHELF'), ('HS', 'HIGH SHELF')];

  final int index;
  final PeqFilter band;
  final NothingPalette palette;
  final String Function(double) hz;
  final VoidCallback onDelete;

  const _BandEditor({
    super.key,
    required this.index,
    required this.band,
    required this.palette,
    required this.hz,
    required this.onDelete,
  });

  void _update(BuildContext context, PeqFilter next) =>
      context.read<EqProvider>().updateSandboxBand(index, next);

  @override
  Widget build(BuildContext context) {
    final c = palette;
    double logPos(double v, double lo, double hi) => math.log(v / lo) / math.log(hi / lo);
    double logVal(double t, double lo, double hi) => lo * math.pow(hi / lo, t).toDouble();

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 32),
      children: [
        Row(
          children: [
            for (final (i, (type, label)) in _types.indexed)
              Expanded(
                child: Padding(
                  padding: EdgeInsets.only(left: i == 0 ? 0 : 8),
                  child: SizedBox(
                    height: 34,
                    child: _Pill(
                      key: Key('sandbox_type_$type'),
                      label: label,
                      selected: band.type == type,
                      palette: c,
                      compact: true,
                      onTap: () {
                        HapticFeedback.selectionClick();
                        _update(context, band.copyWith(type: type));
                      },
                    ),
                  ),
                ),
              ),
          ],
        ),
        const SizedBox(height: 12),
        _slider(context, 'FREQUENCY', hz(band.fc), const Key('sandbox_freq'), logPos(band.fc, SandboxBands.minHz, SandboxBands.maxHz),
            (t) => _update(context, band.copyWith(fc: logVal(t, SandboxBands.minHz, SandboxBands.maxHz)))),
        _slider(context, 'GAIN', '${band.gain > 0 ? '+' : ''}${band.gain.toStringAsFixed(1)} dB', const Key('sandbox_gain'),
            (band.gain + ManualBands.maxDb) / (ManualBands.maxDb * 2),
            (t) => _update(context, band.copyWith(gain: t * ManualBands.maxDb * 2 - ManualBands.maxDb))),
        _slider(context, 'WIDTH (Q)', band.q.toStringAsFixed(2), const Key('sandbox_q'), logPos(band.q, SandboxBands.minQ, SandboxBands.maxQ),
            (t) => _update(context, band.copyWith(q: logVal(t, SandboxBands.minQ, SandboxBands.maxQ)))),
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton(
            key: const Key('sandbox_delete'),
            onPressed: onDelete,
            child: Text('DELETE BAND', style: NothingTypography.monoLabel(isDark: c.dark, color: NothingColors.signalRedBright)),
          ),
        ),
      ],
    );
  }

  Widget _slider(BuildContext context, String label, String value, Key key, double position, ValueChanged<double> onChanged) {
    final c = palette;
    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(child: Text(label, style: NothingTypography.monoLabel(isDark: c.dark))),
              Text(value, style: TextStyle(fontFamily: NothingTypography.fontMono, fontSize: 11, color: c.display)),
            ],
          ),
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              trackHeight: 2,
              activeTrackColor: NothingColors.signalRed,
              inactiveTrackColor: c.line2,
              thumbColor: c.display,
              overlayShape: SliderComponentShape.noOverlay,
              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
            ),
            child: Slider(key: key, value: position.clamp(0.0, 1.0), onChanged: onChanged),
          ),
        ],
      ),
    );
  }
}
