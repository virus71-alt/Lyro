import 'package:flutter/material.dart';
import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_theme.dart';
import '../../core/theme/nothing_typography.dart';

enum NothingButtonVariant {
  primary,
  secondary,
  outline,
  pill,
  danger,
}

class NothingButton extends StatelessWidget {
  final String text;
  final VoidCallback? onPressed;
  final NothingButtonVariant variant;
  final Widget? icon;
  final bool fullWidth;
  final bool isSelected;
  final EdgeInsetsGeometry padding;

  const NothingButton({
    super.key,
    required this.text,
    this.onPressed,
    this.variant = NothingButtonVariant.primary,
    this.icon,
    this.fullWidth = false,
    this.isSelected = false,
    this.padding = const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    Color bg;
    Color fg;
    BorderSide border = BorderSide.none;
    double radius = NothingColors.radiusSm;

    switch (variant) {
      case NothingButtonVariant.primary:
        bg = isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay;
        fg = isDark ? NothingColors.darkBg : NothingColors.lightBg;
        break;
      case NothingButtonVariant.secondary:
        bg = isDark ? NothingColors.darkRaised : NothingColors.lightRaised;
        fg = isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary;
        border = BorderSide(
          color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
          width: 1,
        );
        break;
      case NothingButtonVariant.outline:
        bg = Colors.transparent;
        fg = isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary;
        border = BorderSide(
          color: isDark ? NothingColors.darkLine2 : NothingColors.lightLine2,
          width: 1,
        );
        break;
      case NothingButtonVariant.pill:
        radius = NothingColors.radiusPill;
        if (isSelected) {
          bg = isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay;
          fg = isDark ? NothingColors.darkBg : NothingColors.lightBg;
        } else {
          bg = Colors.transparent;
          fg = isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary;
          border = BorderSide(
            color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
            width: 1,
          );
        }
        break;
      case NothingButtonVariant.danger:
        bg = Colors.transparent;
        fg = isDark ? NothingColors.darkAccentText : NothingColors.lightAccentText;
        border = BorderSide(
          color: isDark
              ? NothingColors.darkAccentText.withValues(alpha: 0.5)
              : NothingColors.lightAccentText.withValues(alpha: 0.5),
          width: 1,
        );
        break;
    }

    final child = Row(
      mainAxisSize: fullWidth ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (icon != null) ...[
          icon!,
          const SizedBox(width: 8),
        ],
        Text(
          text.toUpperCase(),
          style: TextStyle(
            fontFamily: NothingTypography.fontMono,
            fontSize: variant == NothingButtonVariant.pill ? 11 : 12,
            fontWeight: FontWeight.w600,
            letterSpacing: 1.2,
            color: fg,
          ),
        ),
      ],
    );

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onPressed,
        borderRadius: BorderRadius.circular(radius),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeInOut,
          padding: padding,
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(radius),
            border: border == BorderSide.none ? null : Border.fromBorderSide(border),
          ),
          child: child,
        ),
      ),
    );
  }
}
