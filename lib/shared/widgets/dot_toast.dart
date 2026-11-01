import 'package:flutter/material.dart';

import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_typography.dart';

/// Short Nothing-style confirmation ("QUEUED · 3RD", "SLEEP · 30 MIN"), with
/// an optional action such as UNDO.
void showDotToast(
  BuildContext context,
  String message, {
  String? actionLabel,
  VoidCallback? onAction,
  Duration duration = const Duration(milliseconds: 2200),
}) {
  final messenger = ScaffoldMessenger.maybeOf(context);
  if (messenger == null) return;
  messenger
    ..hideCurrentSnackBar()
    ..showSnackBar(
      SnackBar(
        behavior: SnackBarBehavior.floating,
        margin: const EdgeInsets.fromLTRB(20, 0, 20, 24),
        backgroundColor: const Color(0xFF151517),
        elevation: 0,
        duration: duration,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(NothingColors.radiusPill),
          side: const BorderSide(color: NothingColors.darkLine2),
        ),
        content: Row(
          children: [
            Container(
              width: 6,
              height: 6,
              margin: const EdgeInsets.only(right: 10),
              decoration: const BoxDecoration(color: NothingColors.signalRed, shape: BoxShape.circle),
            ),
            Expanded(
              child: Text(
                message.toUpperCase(),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: NothingTypography.dottedBold(
                  isDark: true,
                  fontSize: 14,
                  letterSpacing: 1.2,
                  color: NothingColors.darkDisplay,
                ),
              ),
            ),
          ],
        ),
        action: actionLabel == null
            ? null
            : SnackBarAction(
                label: actionLabel.toUpperCase(),
                textColor: NothingColors.signalRedBright,
                onPressed: onAction ?? () {},
              ),
      ),
    );
}

/// "1ST", "2ND", "3RD", "4TH"...
String ordinal(int n) {
  final mod100 = n % 100;
  if (mod100 >= 11 && mod100 <= 13) return '${n}TH';
  return switch (n % 10) { 1 => '${n}ST', 2 => '${n}ND', 3 => '${n}RD', _ => '${n}TH' };
}
