import 'dart:ui';
import 'package:flutter/material.dart';
import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_theme.dart';

class NothingCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;
  final double radius;
  final bool borderless;
  final Color? customBg;

  const NothingCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16.0),
    this.onTap,
    this.radius = NothingColors.radiusMd,
    this.borderless = false,
    this.customBg,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final bgColor = customBg ?? (isDark ? NothingColors.darkGlass : NothingColors.lightGlass);
    final borderColor = borderless
        ? Colors.transparent
        : (isDark ? NothingColors.darkLine : NothingColors.lightLine);

    Widget content = ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 14, sigmaY: 14),
        child: Container(
          decoration: BoxDecoration(
            color: bgColor,
            borderRadius: BorderRadius.circular(radius),
            border: Border.all(
              color: borderColor,
              width: 1.0,
            ),
          ),
          padding: padding,
          child: child,
        ),
      ),
    );

    if (onTap != null) {
      return Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(radius),
          splashColor: (isDark ? Colors.white : Colors.black).withValues(alpha: 0.06),
          highlightColor: (isDark ? Colors.white : Colors.black).withValues(alpha: 0.04),
          child: content,
        ),
      );
    }

    return content;
  }
}
