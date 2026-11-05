import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_palette.dart';
import '../../core/theme/nothing_typography.dart';
import '../../shared/widgets/dot_matrix.dart';
import '../../shared/widgets/dot_toast.dart';
import '../playlists/widgets/add_to_playlist_sheet.dart';
import 'eq_graph.dart';
import 'eq_model.dart';
import 'eq_provider.dart';
import 'eq_sandbox_screen.dart';
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

          // Saved sounds
          _Presets(palette: c),
          const SizedBox(height: 20),

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
                painter: EqResponsePainter(
                  correction: p.filters,
                  manual: [...ManualBands.filters(eq.manual), ...eq.sandbox],
                  palette: c,
                ),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 14,
            runSpacing: 4,
            children: [
              _legend(c, NothingColors.signalRed, 'IEM correction'),
              _legend(c, c.secondary, 'Fine-tune'),
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
          const SizedBox(height: 22),

          // Sandbox
          Text('SANDBOX', style: NothingTypography.monoLabel(isDark: c.dark)),
          const SizedBox(height: 8),
          Material(
            color: c.surface,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: BorderSide(color: c.line),
            ),
            child: InkWell(
              key: const Key('eq_open_sandbox'),
              borderRadius: BorderRadius.circular(16),
              onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const EqSandboxScreen())),
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Build your own curve',
                            style: TextStyle(
                              fontFamily: NothingTypography.fontUi,
                              fontSize: 15,
                              fontWeight: FontWeight.w700,
                              color: c.display,
                            ),
                          ),
                          const SizedBox(height: 3),
                          Text(
                            eq.sandbox.isEmpty
                                ? 'Drag bands anywhere on the graph'
                                : '${eq.sandbox.length} ${eq.sandbox.length == 1 ? 'band' : 'bands'} active',
                            style: TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 12, color: c.secondary),
                          ),
                        ],
                      ),
                    ),
                    Text(
                      'OPEN',
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
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(width: 10, height: 3, color: color),
          const SizedBox(width: 6),
          Text(label, style: TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 11, color: c.secondary)),
        ],
      );
}

/// Saved sounds: tap to load, hold for update, rename and delete.
class _Presets extends StatelessWidget {
  final NothingPalette palette;
  const _Presets({required this.palette});

  Future<void> _save(BuildContext context) async {
    final eq = context.read<EqProvider>();
    final name = await askPlaylistName(
      context,
      title: 'Save preset',
      initial: 'My sound ${eq.presets.length + 1}',
      hint: 'Preset name',
    );
    if (name == null || name.trim().isEmpty || !context.mounted) return;
    final preset = eq.savePreset(name.trim());
    showDotToast(context, 'Saved · ${preset.name}');
  }

  void _options(BuildContext context, UserEqPreset preset) {
    final c = palette;
    final eq = context.read<EqProvider>();
    showModalBottomSheet(
      context: context,
      backgroundColor: c.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(NothingColors.radiusMd)),
      ),
      builder: (sheet) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              key: const Key('eq_preset_update'),
              leading: Icon(Icons.save_outlined, color: c.display),
              title: Text('UPDATE TO CURRENT SOUND', style: NothingTypography.monoLabel(isDark: c.dark)),
              onTap: () {
                Navigator.pop(sheet);
                eq.updatePreset(preset);
                showDotToast(context, 'Updated · ${preset.name}');
              },
            ),
            ListTile(
              key: const Key('eq_preset_rename'),
              leading: Icon(Icons.edit_outlined, color: c.display),
              title: Text('RENAME', style: NothingTypography.monoLabel(isDark: c.dark)),
              onTap: () async {
                Navigator.pop(sheet);
                final name = await askPlaylistName(context, title: 'Rename preset', initial: preset.name, hint: 'Preset name');
                if (name != null && name.trim().isNotEmpty) eq.renamePreset(preset, name.trim());
              },
            ),
            ListTile(
              key: const Key('eq_preset_delete'),
              leading: const Icon(Icons.delete_outline, color: NothingColors.signalRedBright),
              title: Text('DELETE', style: NothingTypography.monoLabel(isDark: c.dark, color: NothingColors.signalRedBright)),
              onTap: () {
                Navigator.pop(sheet);
                eq.deletePreset(preset);
                showDotToast(context, 'Deleted · ${preset.name}');
              },
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final c = palette;
    final eq = context.watch<EqProvider>();
    final active = eq.activePreset;

    Widget chip(Key key, String label, bool selected, VoidCallback onTap, {VoidCallback? onLongPress}) => Padding(
          padding: const EdgeInsets.only(right: 8),
          child: GestureDetector(
            key: key,
            behavior: HitTestBehavior.opaque,
            onTap: onTap,
            onLongPress: onLongPress,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: selected ? NothingColors.signalRed : null,
                borderRadius: BorderRadius.circular(NothingColors.radiusPill),
                border: Border.all(color: selected ? NothingColors.signalRed : c.line2),
              ),
              child: Text(
                label,
                style: TextStyle(
                  fontFamily: NothingTypography.fontMono,
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  letterSpacing: 0.6,
                  color: selected ? Colors.white : c.display,
                ),
              ),
            ),
          ),
        );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('MY PRESETS', style: NothingTypography.monoLabel(isDark: c.dark)),
        const SizedBox(height: 8),
        SizedBox(
          height: 38,
          child: ListView(
            scrollDirection: Axis.horizontal,
            children: [
              chip(const Key('eq_preset_save'), '+ SAVE', false, () => _save(context)),
              for (final p in eq.presets)
                chip(Key('eq_preset_${p.id}'), p.name.toUpperCase(), p.id == active?.id, () {
                  HapticFeedback.selectionClick();
                  eq.applyPreset(p);
                }, onLongPress: () => _options(context, p)),
            ],
          ),
        ),
      ],
    );
  }
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
