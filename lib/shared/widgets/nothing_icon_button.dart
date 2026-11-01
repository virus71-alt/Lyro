import 'package:flutter/material.dart';
import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_theme.dart';

class NothingIconButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback? onPressed;
  final double size;
  final double iconSize;
  final Color? color;
  final String? tooltip;
  final bool isSelected;
  final bool isCircle;

  const NothingIconButton({
    super.key,
    required this.icon,
    this.onPressed,
    this.size = 40.0,
    this.iconSize = 20.0,
    this.color,
    this.tooltip,
    this.isSelected = false,
    this.isCircle = true,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final iconColor = color ??
        (isSelected
            ? NothingColors.darkAccentText
            : (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay));

    final button = Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onPressed,
        customBorder: isCircle
            ? const CircleBorder()
            : RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(NothingColors.radiusSm),
              ),
        child: SizedBox(
          width: size,
          height: size,
          child: Center(
            child: Icon(
              icon,
              size: iconSize,
              color: onPressed != null
                  ? iconColor
                  : (isDark ? NothingColors.darkMuted : NothingColors.lightMuted),
            ),
          ),
        ),
      ),
    );

    if (tooltip != null) {
      return Tooltip(message: tooltip!, child: button);
    }
    return button;
  }
}

