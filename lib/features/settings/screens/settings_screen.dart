import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/constants/app_constants.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../core/theme/theme_provider.dart';
import '../../../shared/widgets/nothing_button.dart';
import '../../../shared/widgets/nothing_card.dart';
import '../../library/providers/library_provider.dart';
import '../../library/widgets/mini_player.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final themeProvider = context.watch<ThemeProvider>();
    final library = context.watch<LibraryProvider>();

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        title: Text(
          'SETTINGS',
          style: NothingTypography.displayTitle(isDark: isDark, fontSize: 18),
        ),
      ),
      body: Stack(
        fit: StackFit.expand,
        children: [
          Positioned.fill(
            child: CustomPaint(
              painter: NothingDotPainter(
                isDark: isDark,
                spacing: 24.0,
                dotRadius: 1.0,
              ),
            ),
          ),
          Column(
            children: [
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.all(16.0),
                  children: [
                    // Theme Section
                    Text(
                      'APPEARANCE',
                      style: NothingTypography.monoLabel(isDark: isDark),
                    ),
                    const SizedBox(height: 8),
                    NothingCard(
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                      child: Column(
                        children: [
                          _themeOption(
                            context: context,
                            title: 'SYSTEM DEFAULT',
                            mode: ThemeMode.system,
                            current: themeProvider.themeMode,
                            onSelect: () => themeProvider.setThemeMode(ThemeMode.system),
                          ),
                          const Divider(height: 16),
                          _themeOption(
                            context: context,
                            title: 'DARK MONOCHROME',
                            mode: ThemeMode.dark,
                            current: themeProvider.themeMode,
                            onSelect: () => themeProvider.setThemeMode(ThemeMode.dark),
                          ),
                          const Divider(height: 16),
                          _themeOption(
                            context: context,
                            title: 'LIGHT MINIMAL',
                            mode: ThemeMode.light,
                            current: themeProvider.themeMode,
                            onSelect: () => themeProvider.setThemeMode(ThemeMode.light),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 24),
                    // Media Library Section
                    Text(
                      'MUSIC LIBRARY',
                      style: NothingTypography.monoLabel(isDark: isDark),
                    ),
                    const SizedBox(height: 8),
                    NothingCard(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'RESCAN DEVICE STORAGE',
                                    style: TextStyle(
                                      fontFamily: NothingTypography.fontUi,
                                      fontSize: 14,
                                      fontWeight: FontWeight.w600,
                                      color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                    ),
                                  ),
                                  const SizedBox(height: 3),
                                  Text(
                                    'Scan MediaStore for new and updated audio tracks',
                                    style: NothingTypography.bodySmall(isDark: isDark),
                                  ),
                                ],
                              ),
                            ],
                          ),
                          const SizedBox(height: 14),
                          NothingButton(
                            text: library.isLoading ? 'SCANNING...' : 'START RESCAN',
                            variant: NothingButtonVariant.primary,
                            fullWidth: true,
                            onPressed: library.isLoading
                                ? null
                                : () async {
                                    await library.loadLibrary(isRescan: true);
                                    if (context.mounted) {
                                      ScaffoldMessenger.of(context).showSnackBar(
                                        SnackBar(
                                          backgroundColor: isDark ? NothingColors.darkRaised : NothingColors.lightRaised,
                                          content: Text(
                                            'Library scan completed. Found ${library.songs.length} tracks.',
                                            style: TextStyle(
                                              fontFamily: NothingTypography.fontMono,
                                              fontSize: 12,
                                              color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                            ),
                                          ),
                                        ),
                                      );
                                    }
                                  },
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 24),
                    // About Lyro
                    Text(
                      'ABOUT',
                      style: NothingTypography.monoLabel(isDark: isDark),
                    ),
                    const SizedBox(height: 8),
                    NothingCard(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              ClipRRect(
                                borderRadius: BorderRadius.circular(10),
                                child: Image.asset(
                                  'assets/icon/icon.png',
                                  width: 44,
                                  height: 44,
                                  fit: BoxFit.cover,
                                ),
                              ),
                              const SizedBox(width: 14),
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Text(
                                        AppConstants.appName.toUpperCase(),
                                        style: NothingTypography.displayTitle(
                                          isDark: isDark,
                                          fontSize: 22,
                                        ),
                                      ),
                                      const SizedBox(width: 8),
                                      Container(
                                        width: 6,
                                        height: 6,
                                        decoration: const BoxDecoration(
                                          color: NothingColors.darkAccent,
                                          shape: BoxShape.circle,
                                        ),
                                      ),
                                    ],
                                  ),
                                  Text(
                                    'v${AppConstants.appVersion}',
                                    style: NothingTypography.monoLabel(isDark: isDark),
                                  ),
                                ],
                              ),
                            ],
                          ),
                          const SizedBox(height: 6),
                          Text(
                            AppConstants.appTagline,
                            style: NothingTypography.editorialHero(isDark: isDark),
                          ),
                          const SizedBox(height: 12),
                          Text(
                            'Lyro is a modern, cross-platform offline music player inspired by Auxio and engineered around the Nothing OS design language. It natively plays lossless FLAC, MP3, WAV, AAC, and OGG formats with low latency and lockscreen background playback.',
                            style: NothingTypography.bodyMedium(isDark: isDark),
                          ),
                          const SizedBox(height: 16),
                          _infoRow(isDark, 'VERSION', AppConstants.appVersion),
                          const SizedBox(height: 8),
                          _infoRow(isDark, 'APPLICATION ID', AppConstants.bundleId),
                          const SizedBox(height: 8),
                          _infoRow(isDark, 'DECODER', 'ExoPlayer (Android) / AVPlayer (iOS)'),
                          const SizedBox(height: 8),
                          _infoRow(isDark, 'FLAC LOSSLESS', 'Supported (16/24-bit PCM)'),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const MiniPlayer(),
            ],
          ),
        ],
      ),
    );
  }

  Widget _themeOption({
    required BuildContext context,
    required String title,
    required ThemeMode mode,
    required ThemeMode current,
    required VoidCallback onSelect,
  }) {
    final isSelected = mode == current;
    final isDark = context.isDark;

    return InkWell(
      onTap: onSelect,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6.0),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              title,
              style: TextStyle(
                fontFamily: NothingTypography.fontMono,
                fontSize: 12,
                fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                color: isSelected
                    ? (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay)
                    : (isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary),
              ),
            ),
            if (isSelected)
              const Icon(Icons.check, size: 18, color: NothingColors.darkAccentText),
          ],
        ),
      ),
    );
  }

  Widget _infoRow(bool isDark, String label, String value) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 120,
          child: Text(
            label,
            style: NothingTypography.monoLabel(isDark: isDark),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: TextStyle(
              fontFamily: NothingTypography.fontMono,
              fontSize: 11,
              color: isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary,
            ),
          ),
        ),
      ],
    );
  }
}
