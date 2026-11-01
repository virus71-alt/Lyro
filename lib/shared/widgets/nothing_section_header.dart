import 'package:flutter/material.dart';
import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_theme.dart';
import '../../core/theme/nothing_typography.dart';

class NothingSectionHeader extends StatelessWidget {
  final String title;
  final String? count;
  final Widget? trailing;
  final bool useDisplayFont;

  const NothingSectionHeader({
    super.key,
    required this.title,
    this.count,
    this.trailing,
    this.useDisplayFont = false,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.baseline,
        textBaseline: TextBaseline.alphabetic,
        children: [
          Expanded(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Text(
                  useDisplayFont ? title.toUpperCase() : title,
                  style: useDisplayFont
                      ? NothingTypography.displayTitle(isDark: isDark)
                      : NothingTypography.headlineMedium(isDark: isDark),
                ),
                if (count != null) ...[
                  const SizedBox(width: 8),
                  Text(
                    count!.toUpperCase(),
                    style: NothingTypography.dottedBold(
                      isDark: isDark,
                      fontSize: 13,
                      letterSpacing: 0.6,
                      color: isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary,
                    ),
                  ),
                ],
              ],
            ),
          ),
          ?trailing,
        ],
      ),
    );
  }
}
