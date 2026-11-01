import 'package:flutter/material.dart';
import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_dot_painter.dart';
import '../../core/theme/nothing_theme.dart';

class NothingScaffold extends StatelessWidget {
  final PreferredSizeWidget? appBar;
  final Widget body;
  final Widget? bottomNavigationBar;
  final Widget? miniPlayer;
  final bool showDots;

  const NothingScaffold({
    super.key,
    this.appBar,
    required this.body,
    this.bottomNavigationBar,
    this.miniPlayer,
    this.showDots = true,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: appBar,
      body: Stack(
        fit: StackFit.expand,
        children: [
          // Dot-matrix field behind content
          if (showDots)
            Positioned.fill(
              child: CustomPaint(
                painter: NothingDotPainter(
                  isDark: isDark,
                  spacing: 24.0,
                  dotRadius: 1.0,
                ),
              ),
            ),
          // Content with miniplayer if present
          Column(
            children: [
              Expanded(child: body),
              ?miniPlayer,
            ],
          ),
        ],
      ),
      bottomNavigationBar: bottomNavigationBar,
    );
  }
}
