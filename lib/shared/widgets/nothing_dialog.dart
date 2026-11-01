import 'dart:ui';
import 'package:flutter/material.dart';
import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_theme.dart';
import '../../core/theme/nothing_typography.dart';
import 'nothing_button.dart';

class NothingDialog extends StatelessWidget {
  final String title;
  final String content;
  final String confirmText;
  final String? cancelText;
  final VoidCallback onConfirm;
  final VoidCallback? onCancel;
  final bool isDestructive;

  const NothingDialog({
    super.key,
    required this.title,
    required this.content,
    required this.confirmText,
    this.cancelText,
    required this.onConfirm,
    this.onCancel,
    this.isDestructive = false,
  });

  static Future<bool?> show({
    required BuildContext context,
    required String title,
    required String content,
    required String confirmText,
    String? cancelText,
    bool isDestructive = false,
  }) {
    return showDialog<bool>(
      context: context,
      barrierColor: Colors.black.withValues(alpha: 0.6),
      builder: (ctx) => NothingDialog(
        title: title,
        content: content,
        confirmText: confirmText,
        cancelText: cancelText,
        isDestructive: isDestructive,
        onConfirm: () => Navigator.of(ctx).pop(true),
        onCancel: () => Navigator.of(ctx).pop(false),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    return Dialog(
      backgroundColor: Colors.transparent,
      elevation: 0,
      insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(NothingColors.radiusMd),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
          child: Container(
            decoration: BoxDecoration(
              color: isDark ? NothingColors.darkGlass : NothingColors.lightGlass,
              borderRadius: BorderRadius.circular(NothingColors.radiusMd),
              border: Border.all(
                color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
                width: 1,
              ),
            ),
            padding: const EdgeInsets.all(22),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title.toUpperCase(),
                  style: NothingTypography.displayTitle(
                    isDark: isDark,
                    fontSize: 18,
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  content,
                  style: NothingTypography.bodyLarge(isDark: isDark),
                ),
                const SizedBox(height: 24),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    if (cancelText != null) ...[
                      NothingButton(
                        text: cancelText!,
                        variant: NothingButtonVariant.outline,
                        onPressed: onCancel ?? () => Navigator.of(context).pop(),
                      ),
                      const SizedBox(width: 12),
                    ],
                    NothingButton(
                      text: confirmText,
                      variant: isDestructive
                          ? NothingButtonVariant.danger
                          : NothingButtonVariant.primary,
                      onPressed: onConfirm,
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
