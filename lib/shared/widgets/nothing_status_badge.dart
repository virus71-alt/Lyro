import 'package:flutter/material.dart';
import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_theme.dart';
import '../../core/theme/nothing_typography.dart';

enum BadgeType {
  neutral,
  signalRed,
  success,
  flacHiRes,
}

class NothingStatusBadge extends StatelessWidget {
  final String label;
  final BadgeType type;
  final bool showDot;

  const NothingStatusBadge({
    super.key,
    required this.label,
    this.type = BadgeType.neutral,
    this.showDot = false,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    Color bg;
    Color fg;
    Color dotColor;
    BorderSide border = BorderSide.none;

    switch (type) {
      case BadgeType.neutral:
        bg = isDark ? NothingColors.darkRaised : NothingColors.lightRaised;
        fg = isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary;
        dotColor = fg;
        break;
      case BadgeType.signalRed:
        bg = NothingColors.darkAccent.withValues(alpha: 0.15);
        fg = isDark ? NothingColors.darkAccentText : NothingColors.lightAccentText;
        dotColor = NothingColors.darkAccent;
        border = BorderSide(
          color: (isDark ? NothingColors.darkAccentText : NothingColors.lightAccentText).withValues(alpha: 0.3),
          width: 1,
        );
        break;
      case BadgeType.success:
        bg = (isDark ? NothingColors.darkSuccess : NothingColors.lightSuccess).withValues(alpha: 0.15);
        fg = isDark ? NothingColors.darkSuccess : NothingColors.lightSuccess;
        dotColor = fg;
        break;
      case BadgeType.flacHiRes:
        bg = isDark ? NothingColors.darkRaised : NothingColors.lightRaised;
        fg = isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay;
        dotColor = NothingColors.darkAccent;
        border = BorderSide(
          color: isDark ? NothingColors.darkLine2 : NothingColors.lightLine2,
          width: 1,
        );
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(NothingColors.radiusSm),
        border: border == BorderSide.none ? null : Border.fromBorderSide(border),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (showDot) ...[
            Container(
              width: 5,
              height: 5,
              decoration: BoxDecoration(
                color: dotColor,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 5),
          ],
          Text(
            label.toUpperCase(),
            style: NothingTypography.monoBadge(
              isDark: isDark,
              color: fg,
            ),
          ),
        ],
      ),
    );
  }
}
