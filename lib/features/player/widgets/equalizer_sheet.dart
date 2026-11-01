import 'package:flutter/material.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../shared/widgets/nothing_button.dart';
import '../../../shared/widgets/nothing_status_badge.dart';

/// Modal bottom sheet for Equalizer.
/// Follows Nothing OS aesthetic and transparently communicates Phase 2 DSP integration.
class EqualizerSheet extends StatelessWidget {
  const EqualizerSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const EqualizerSheet(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final bands = [
      '31Hz', '63Hz', '125Hz', '250Hz', '500Hz',
      '1kHz', '2kHz', '4kHz', '8kHz', '16kHz',
    ];

    final presets = ['FLAT', 'BASS BOOST', 'NOTHING SIGNATURE', 'VOCAL', 'TREBLE'];

    return Container(
      decoration: const BoxDecoration(
        color: NothingColors.darkSurface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        border: Border(
          top: BorderSide(color: NothingColors.darkLine, width: 1.0),
        ),
      ),
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
      child: Stack(
        children: [
          Positioned.fill(
            child: CustomPaint(
              painter: NothingDotPainter(
                isDark: true,
                spacing: 24.0,
                dotRadius: 0.8,
              ),
            ),
          ),
          Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Drag Handle
              Center(
                child: Container(
                  width: 36,
                  height: 4,
                  decoration: BoxDecoration(
                    color: NothingColors.darkLine2,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 16),

              // Title and Status
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'EQUALIZER',
                    style: NothingTypography.displayTitle(isDark: true, fontSize: 18),
                  ),
                  const NothingStatusBadge(
                    label: 'COMING IN PHASE 2',
                    type: BadgeType.signalRed,
                    showDot: true,
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                'HARDWARE-ACCELERATED 10-BAND PARAMETRIC DSP',
                style: NothingTypography.monoLabel(isDark: true),
              ),
              const SizedBox(height: 18),

              // Presets Row
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: presets.map((preset) {
                    final isSelected = preset == 'NOTHING SIGNATURE';
                    return Container(
                      margin: const EdgeInsets.only(right: 8),
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                      decoration: BoxDecoration(
                        color: isSelected ? NothingColors.signalRed : NothingColors.darkRaised,
                        borderRadius: BorderRadius.circular(NothingColors.radiusPill),
                        border: Border.all(
                          color: isSelected ? NothingColors.signalRed : NothingColors.darkLine,
                          width: 1.0,
                        ),
                      ),
                      child: Text(
                        preset,
                        style: TextStyle(
                          fontFamily: NothingTypography.fontMono,
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          color: isSelected ? Colors.white : NothingColors.darkSecondary,
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ),
              const SizedBox(height: 24),

              // 10-Band EQ Visualizer Representation
              SizedBox(
                height: 160,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: bands.map((band) {
                    return Expanded(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: [
                          Text(
                            '+0dB',
                            style: TextStyle(
                              fontFamily: NothingTypography.fontMono,
                              fontSize: 8,
                              color: NothingColors.darkMuted,
                            ),
                          ),
                          const SizedBox(height: 6),
                          Expanded(
                            child: Stack(
                              alignment: Alignment.center,
                              children: [
                                // Center zero line
                                Container(
                                  width: 2.0,
                                  color: NothingColors.darkLine,
                                ),
                                // Slider Thumb
                                Positioned(
                                  child: Container(
                                    width: 14,
                                    height: 14,
                                    decoration: BoxDecoration(
                                      shape: BoxShape.circle,
                                      color: NothingColors.darkDisplay,
                                      border: Border.all(
                                        color: NothingColors.signalRed,
                                        width: 1.5,
                                      ),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            band,
                            style: TextStyle(
                              fontFamily: NothingTypography.fontMono,
                              fontSize: 9,
                              color: NothingColors.darkSecondary,
                            ),
                          ),
                        ],
                      ),
                    );
                  }).toList(),
                ),
              ),
              const SizedBox(height: 20),

              // Transparent Notice
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: NothingColors.darkRaised,
                  borderRadius: BorderRadius.circular(NothingColors.radiusMd),
                  border: Border.all(color: NothingColors.darkLine),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.info_outline,
                      size: 16,
                      color: NothingColors.signalRed,
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        'Full 10-band audio equalization with bit-perfect decoding will unlock in Lyro Phase 2.',
                        style: NothingTypography.bodySmall(isDark: true),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // Dismiss Button
              NothingButton(
                text: 'DISMISS',
                variant: NothingButtonVariant.secondary,
                fullWidth: true,
                onPressed: () => Navigator.of(context).pop(),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
