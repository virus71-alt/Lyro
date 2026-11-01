import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';

class NothingNavigationItem {
  final String label;
  final IconData icon;
  final IconData activeIcon;

  const NothingNavigationItem({
    required this.label,
    required this.icon,
    required this.activeIcon,
  });
}

class NothingNavigationBar extends StatelessWidget {
  final int currentIndex;
  final ValueChanged<int> onTap;
  final List<NothingNavigationItem> items;

  const NothingNavigationBar({
    super.key,
    required this.currentIndex,
    required this.onTap,
    required this.items,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    return ClipRRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
        child: Container(
          decoration: BoxDecoration(
            color: isDark ? NothingColors.darkGlass : NothingColors.lightGlass,
            border: Border(
              top: BorderSide(
                color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
                width: 1.0,
              ),
            ),
          ),
          child: SafeArea(
            top: false,
            child: SizedBox(
              height: 60,
              child: Row(
                children: List.generate(items.length, (index) {
                  final item = items[index];
                  final isSelected = index == currentIndex;

                  return Expanded(
                    child: GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onTap: () {
                        if (index != currentIndex) {
                          HapticFeedback.lightImpact();
                          onTap(index);
                        }
                      },
                      child: Container(
                        padding: const EdgeInsets.symmetric(vertical: 6.0),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            // Icon
                            Icon(
                              isSelected ? item.activeIcon : item.icon,
                              size: 22,
                              color: isSelected
                                  ? (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay)
                                  : (isDark ? NothingColors.darkMuted : NothingColors.lightMuted),
                            ),
                            const SizedBox(height: 3),

                            // Label in Mono
                            Text(
                              item.label.toUpperCase(),
                              style: TextStyle(
                                fontFamily: NothingTypography.fontMono,
                                fontSize: 10,
                                fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                                letterSpacing: 1.2,
                                color: isSelected
                                    ? (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay)
                                    : (isDark ? NothingColors.darkMuted : NothingColors.lightMuted),
                              ),
                            ),
                            const SizedBox(height: 3),

                            // Signature Nothing OS Signal Red Active Dot
                            Container(
                              width: 4,
                              height: 4,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: isSelected
                                    ? NothingColors.signalRed
                                    : Colors.transparent,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  );
                }),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

