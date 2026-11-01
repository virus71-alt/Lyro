import 'package:flutter/material.dart';
import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_theme.dart';
import '../../core/theme/nothing_typography.dart';

class NothingTextField extends StatelessWidget {
  final TextEditingController? controller;
  final String? hintText;
  final String? labelText;
  final ValueChanged<String>? onChanged;
  final VoidCallback? onClear;
  final Widget? prefixIcon;
  final bool autofocus;

  const NothingTextField({
    super.key,
    this.controller,
    this.hintText,
    this.labelText,
    this.onChanged,
    this.onClear,
    this.prefixIcon,
    this.autofocus = false,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (labelText != null) ...[
          Text(
            labelText!.toUpperCase(),
            style: NothingTypography.monoLabel(isDark: isDark),
          ),
          const SizedBox(height: 6),
        ],
        Container(
          decoration: BoxDecoration(
            color: isDark ? NothingColors.darkSurface : NothingColors.lightSurface,
            borderRadius: BorderRadius.circular(NothingColors.radiusSm),
            border: Border.all(
              color: isDark ? NothingColors.darkLine2 : NothingColors.lightLine2,
              width: 1.0,
            ),
          ),
          child: TextField(
            controller: controller,
            autofocus: autofocus,
            onChanged: onChanged,
            cursorColor: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
            style: TextStyle(
              fontFamily: NothingTypography.fontUi,
              fontSize: 14,
              color: isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary,
            ),
            decoration: InputDecoration(
              isDense: true,
              hintText: hintText?.toUpperCase(),
              hintStyle: TextStyle(
                fontFamily: NothingTypography.fontMono,
                fontSize: 12,
                letterSpacing: 0.8,
                color: isDark ? NothingColors.darkMuted : NothingColors.lightMuted,
              ),
              prefixIcon: prefixIcon,
              suffixIcon: (controller?.text.isNotEmpty == true && onClear != null)
                  ? IconButton(
                      icon: Icon(
                        Icons.close,
                        size: 16,
                        color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                      ),
                      onPressed: onClear,
                    )
                  : null,
              border: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            ),
          ),
        ),
      ],
    );
  }
}

