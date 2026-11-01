import 'package:flutter/material.dart';
import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_theme.dart';

class NothingSwitch extends StatelessWidget {
  final bool value;
  final ValueChanged<bool> onChanged;

  const NothingSwitch({
    super.key,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    return GestureDetector(
      onTap: () => onChanged(!value),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeInOut,
        width: 44,
        height: 24,
        padding: const EdgeInsets.all(2),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(NothingColors.radiusPill),
          color: value
              ? (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay)
              : (isDark ? NothingColors.darkSurface : NothingColors.lightRaised),
          border: Border.all(
            color: isDark ? NothingColors.darkLine2 : NothingColors.lightLine2,
            width: 1,
          ),
        ),
        child: Align(
          alignment: value ? Alignment.centerRight : Alignment.centerLeft,
          child: Container(
            width: 18,
            height: 18,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: value
                  ? (isDark ? NothingColors.darkBg : NothingColors.lightBg)
                  : (isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary),
            ),
          ),
        ),
      ),
    );
  }
}

