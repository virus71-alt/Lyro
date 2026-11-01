import 'package:flutter/material.dart';
import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_theme.dart';

class NothingProgressIndicator extends StatelessWidget {
  final double? value; // 0.0 to 1.0 or null for indeterminate

  const NothingProgressIndicator({super.key, this.value});

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    if (value != null) {
      return Container(
        height: 2,
        width: double.infinity,
        decoration: BoxDecoration(
          color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
          borderRadius: BorderRadius.circular(1),
        ),
        child: FractionallySizedBox(
          alignment: Alignment.centerLeft,
          widthFactor: value!.clamp(0.0, 1.0),
          child: Container(
            decoration: BoxDecoration(
              color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
              borderRadius: BorderRadius.circular(1),
            ),
          ),
        ),
      );
    }

    return SizedBox(
      height: 2,
      child: LinearProgressIndicator(
        backgroundColor: isDark ? NothingColors.darkLine : NothingColors.lightLine,
        valueColor: AlwaysStoppedAnimation<Color>(
          isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
        ),
      ),
    );
  }
}

