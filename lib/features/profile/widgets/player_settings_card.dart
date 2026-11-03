import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/settings/app_settings_provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../shared/widgets/nothing_card.dart';

/// Profile → PLAYER: speaker colourway, track fades and reduce motion.
class PlayerSettingsCard extends StatelessWidget {
  const PlayerSettingsCard({super.key});

  static const _fadeSteps = [0, 2, 4, 6, 8];

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final settings = context.watch<AppSettingsProvider>();
    final display = dark ? NothingColors.darkDisplay : NothingColors.lightDisplay;
    final secondary = dark ? NothingColors.darkSecondary : NothingColors.lightSecondary;

    TextStyle title() => TextStyle(
          fontFamily: NothingTypography.fontMono,
          fontSize: 12,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.8,
          color: display,
        );
    TextStyle hint() => TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 11, color: secondary);

    final fadeIndex = _fadeSteps.indexOf(settings.fadeSeconds).clamp(0, _fadeSteps.length - 1);

    return NothingCard(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Speaker colourway
          Text('SPEAKER COLOUR', style: title()),
          const SizedBox(height: 2),
          Text('Colourways for the Now Playing speaker', style: hint()),
          const SizedBox(height: 12),
          Row(
            children: [
              _swatch(
                context,
                key: const Key('colorway_auto'),
                label: 'AUTO',
                colors: const [Color(0xFF3B3D44), Color(0xFFB9BBC0)],
                selected: settings.speakerColorIndex < 0,
                onTap: () => settings.setSpeakerColor(-1),
              ),
              for (final (i, c) in SpeakerColorway.values.indexed)
                _swatch(
                  context,
                  key: Key('colorway_${c.name}'),
                  label: c.label.toUpperCase(),
                  colors: [c.body, c.knob],
                  selected: settings.speakerColorIndex == i,
                  onTap: () => settings.setSpeakerColor(i),
                ),
            ],
          ),
          Divider(height: 28, color: dark ? NothingColors.darkLine : NothingColors.lightLine),

          // Fades
          Row(
            children: [
              Expanded(child: Text('TRACK TRANSITIONS', style: title())),
              Text(
                settings.fadeSeconds == 0 ? 'GAPLESS' : 'FADE ${settings.fadeSeconds}S',
                style: NothingTypography.dottedBold(isDark: dark, fontSize: 14, color: NothingColors.signalRedBright),
              ),
            ],
          ),
          const SizedBox(height: 2),
          Text('Gapless keeps albums seamless; a fade softens track changes', style: hint()),
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              activeTrackColor: NothingColors.signalRed,
              inactiveTrackColor: dark ? NothingColors.darkLine2 : NothingColors.lightLine2,
              thumbColor: display,
              overlayShape: SliderComponentShape.noOverlay,
              trackHeight: 2,
            ),
            child: Slider(
              key: const Key('fade_slider'),
              min: 0,
              max: (_fadeSteps.length - 1).toDouble(),
              divisions: _fadeSteps.length - 1,
              value: fadeIndex.toDouble(),
              onChanged: (v) {
                final secs = _fadeSteps[v.round()];
                if (secs != settings.fadeSeconds) {
                  HapticFeedback.selectionClick();
                  settings.setFadeSeconds(secs);
                }
              },
            ),
          ),
          Divider(height: 20, color: dark ? NothingColors.darkLine : NothingColors.lightLine),

          // Reduce motion
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('REDUCE MOTION', style: title()),
                    const SizedBox(height: 2),
                    Text(
                      MediaQuery.disableAnimationsOf(context)
                          ? 'On via Android\'s "Remove animations" setting'
                          : 'Still logo, no spinning art or sound waves',
                      style: hint(),
                    ),
                  ],
                ),
              ),
              Switch(
                key: const Key('reduce_motion_switch'),
                value: settings.motionReduced(context),
                activeThumbColor: NothingColors.darkDisplay,
                activeTrackColor: NothingColors.signalRed,
                onChanged: MediaQuery.disableAnimationsOf(context)
                    ? null
                    : (v) {
                        HapticFeedback.selectionClick();
                        settings.setReduceMotion(v);
                      },
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _swatch(
    BuildContext context, {
    required Key key,
    required String label,
    required List<Color> colors,
    required bool selected,
    required VoidCallback onTap,
  }) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Expanded(
      child: GestureDetector(
        key: key,
        onTap: () {
          HapticFeedback.selectionClick();
          onTap();
        },
        child: Semantics(
          button: true,
          selected: selected,
          label: 'Speaker colour $label',
          child: Column(
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [colors[0], colors[0], colors[1]],
                    stops: const [0, 0.62, 0.62],
                  ),
                  border: Border.all(
                    color: selected
                        ? NothingColors.signalRed
                        : (dark ? NothingColors.darkLine2 : NothingColors.lightLine2),
                    width: selected ? 2.2 : 1,
                  ),
                ),
              ),
              const SizedBox(height: 6),
              Text(
                label,
                style: TextStyle(
                  fontFamily: NothingTypography.fontMono,
                  fontSize: 8.5,
                  letterSpacing: 0.8,
                  color: selected
                      ? (dark ? NothingColors.darkDisplay : NothingColors.lightDisplay)
                      : (dark ? NothingColors.darkMuted : NothingColors.lightMuted),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
