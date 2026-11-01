import 'package:flutter/material.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../library/screens/folders_screen.dart';
import '../../library/screens/home_screen.dart';
import '../../library/widgets/mini_player.dart';
import '../../profile/screens/profile_screen.dart';
import '../widgets/nothing_navigation_bar.dart';

class MainNavigationScreen extends StatefulWidget {
  final int initialIndex;

  const MainNavigationScreen({
    super.key,
    this.initialIndex = 0,
  });

  @override
  State<MainNavigationScreen> createState() => _MainNavigationScreenState();
}

class _MainNavigationScreenState extends State<MainNavigationScreen> {
  late int _currentIndex;

  static const List<NothingNavigationItem> _navItems = [
    NothingNavigationItem(
      label: 'Home',
      icon: Icons.home_outlined,
      activeIcon: Icons.home_rounded,
    ),
    NothingNavigationItem(
      label: 'Folders',
      icon: Icons.folder_outlined,
      activeIcon: Icons.folder_rounded,
    ),
    NothingNavigationItem(
      label: 'Profile',
      icon: Icons.person_outline,
      activeIcon: Icons.person_rounded,
    ),
  ];

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialIndex;
  }

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      body: Stack(
        fit: StackFit.expand,
        children: [
          // 1. IndexedStack preserves scroll state & memory for each tab
          IndexedStack(
            index: _currentIndex,
            children: const [
              HomeScreen(isTab: true),
              FoldersScreen(isTab: true),
              ProfileScreen(isTab: true),
            ],
          ),

          // 2. Persistent Floating MiniPlayer + Nothing OS Bottom Navigation Bar
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Mini Player (automatically collapses to zero height when no song is active)
                const MiniPlayer(hasBottomNav: true),

                // Nothing OS Frosted Bottom Navigation Bar: Home | Folders | Profile
                NothingNavigationBar(
                  currentIndex: _currentIndex,
                  onTap: (index) {
                    setState(() {
                      _currentIndex = index;
                    });
                  },
                  items: _navItems,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

