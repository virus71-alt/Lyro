import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/settings/app_settings_provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../shared/widgets/nothing_card.dart';
import '../../player/widgets/cmf_speaker_3d.dart';
import '../../player/widgets/speaker_preview.dart';

/// Profile → SPEAKER STYLE: which 3D hardware model Now Playing shows.
class SpeakerStyleCard extends StatelessWidget {
  const SpeakerStyleCard({super.key});

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final settings = context.watch<AppSettingsProvider>();
    final colorway = settings.colorway(Theme.of(context).brightness);
    final logo = colorway.body.computeLuminance() > 0.3
        ? const Color(0xCC1C1C1C)
        : const Color(0xB8FFFFFF);

    return NothingCard(
      padding: const EdgeInsets.fromLTRB(10, 12, 10, 12),
      child: LayoutBuilder(
        builder: (context, box) {
          final tile = (box.maxWidth - 3 * 6) / SpeakerShape.values.length;
          return Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              for (final (i, shape) in SpeakerShape.values.indexed)
                _tile(
                  dark: dark,
                  key: Key('speaker_style_${shape.label.toLowerCase()}'),
                  shape: shape,
                  size: tile,
                  selected: settings.speakerShape == shape,
                  colorway: colorway,
                  logo: logo,
                  onTap: () {
                    HapticFeedback.selectionClick();
                    settings.setSpeakerStyle(i);
                  },
                ),
            ],
          );
        },
      ),
    );
  }

  Widget _tile({
    required bool dark,
    required Key key,
    required SpeakerShape shape,
    required double size,
    required bool selected,
    required SpeakerColorway colorway,
    required Color logo,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      key: key,
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: Semantics(
        button: true,
        selected: selected,
        label: '${shape.label} speaker',
        child: Column(
          children: [
            Container(
              width: size,
              height: size * 1.25,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(NothingColors.radiusSm),
                border: Border.all(
                  color: selected
                      ? NothingColors.signalRed
                      : (dark ? NothingColors.darkLine : NothingColors.lightLine),
                  width: selected ? 2 : 1,
                ),
              ),
              child: Center(
                child: SpeakerPreview(
                  shape: shape,
                  body: colorway.body,
                  knob: colorway.knob,
                  dial: colorway.dial,
                  logoColor: logo,
                  width: size - 8,
                  height: size * 1.25 - 12,
                ),
              ),
            ),
            const SizedBox(height: 6),
            Text(
              shape.label.toUpperCase(),
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
    );
  }
}
