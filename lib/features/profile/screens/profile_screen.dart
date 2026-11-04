import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../core/constants/app_constants.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../core/theme/theme_provider.dart';
import '../../../shared/widgets/nothing_button.dart';
import '../../../shared/widgets/nothing_card.dart';
import '../../../shared/widgets/nothing_status_badge.dart';
import '../../library/providers/library_provider.dart';
import '../../player/widgets/equalizer_sheet.dart';
import '../../search/screens/search_screen.dart';
import '../../../shared/widgets/dot_matrix.dart';
import '../providers/profile_provider.dart';
import '../widgets/edit_profile_sheet.dart';
import '../widgets/player_settings_card.dart';
import '../widgets/web_stream_card.dart';

class ProfileScreen extends StatelessWidget {
  final bool isTab;

  const ProfileScreen({
    super.key,
    this.isTab = true,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final library = context.watch<LibraryProvider>();
    final themeProvider = context.watch<ThemeProvider>();
    final profile = context.watch<ProfileProvider>();

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      body: Stack(
        fit: StackFit.expand,
        children: [
          // Background Dot Grid
          Positioned.fill(
            child: CustomPaint(
              painter: NothingDotPainter(
                isDark: isDark,
                spacing: 24.0,
                dotRadius: 1.0,
              ),
            ),
          ),

          SafeArea(
            child: Column(
              children: [
                // Header: PROFILE ●
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20.0, vertical: 12.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          Text(
                            'PROFILE',
                            style: NothingTypography.displayHero(isDark: isDark, fontSize: 36),
                          ),
                          const SizedBox(width: 8),
                          Container(
                            width: 8,
                            height: 8,
                            decoration: const BoxDecoration(
                              color: NothingColors.darkAccent,
                              shape: BoxShape.circle,
                            ),
                          ),
                        ],
                      ),
                      IconButton(
                        icon: Icon(
                          Icons.search,
                          size: 22,
                          color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                        ),
                        onPressed: () {
                          Navigator.of(context).push(
                            MaterialPageRoute(builder: (_) => const SearchScreen()),
                          );
                        },
                        tooltip: 'Search',
                      ),
                    ],
                  ),
                ),

                // Main Scrollable Area
                Expanded(
                  child: ListView(
                    padding: EdgeInsets.fromLTRB(20.0, 4.0, 20.0, isTab ? 140.0 : 24.0),
                    children: [
                      // 1. Listener Identity Card
                      NothingCard(
                        padding: const EdgeInsets.all(20.0),
                        child: Column(
                          children: [
                            // Dot-matrix avatar + name (tap either to edit)
                            GestureDetector(
                              key: const Key('profile_edit'),
                              onTap: () => EditProfileSheet.show(context),
                              child: Column(
                                children: [
                                  Stack(
                                    clipBehavior: Clip.none,
                                    children: [
                                      NothingAvatar(
                                        avatarId: profile.avatarId,
                                        name: profile.name,
                                        size: 84,
                                      ),
                                      Positioned(
                                        right: -2,
                                        bottom: -2,
                                        child: Container(
                                          width: 26,
                                          height: 26,
                                          decoration: BoxDecoration(
                                            shape: BoxShape.circle,
                                            color: NothingColors.darkDisplay,
                                            border: Border.all(
                                              color: isDark ? NothingColors.darkSurface : NothingColors.lightSurface,
                                              width: 2,
                                            ),
                                          ),
                                          child: const Icon(Icons.edit, size: 13, color: Colors.black),
                                        ),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 14),
                                  Text(
                                    profile.name.toUpperCase(),
                              style: TextStyle(
                                fontFamily: NothingTypography.fontMono,
                                      fontSize: 16,
                                      fontWeight: FontWeight.w700,
                                      letterSpacing: 1.5,
                                      color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(height: 4),

                            Text(
                              'OFFLINE · LOSSLESS PLAYBACK',
                              style: TextStyle(
                                fontFamily: NothingTypography.fontMono,
                                fontSize: 10,
                                letterSpacing: 1.0,
                                color: isDark ? NothingColors.darkMuted : NothingColors.lightMuted,
                              ),
                            ),
                            const SizedBox(height: 20),

                            // Stats 4-Column Grid
                            Container(
                              padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 8),
                              decoration: BoxDecoration(
                                color: isDark ? const Color(0xFF111111) : const Color(0xFFF7F7F7),
                                borderRadius: BorderRadius.circular(NothingColors.radiusSm),
                                border: Border.all(
                                  color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
                                  width: 1.0,
                                ),
                              ),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.spaceAround,
                                children: [
                                  _statColumn(isDark, count: '${library.songs.length}', label: 'SONGS'),
                                  _divider(isDark),
                                  _statColumn(isDark, count: '${library.albums.length}', label: 'ALBUMS'),
                                  _divider(isDark),
                                  _statColumn(isDark, count: '${library.artists.length}', label: 'ARTISTS'),
                                  _divider(isDark),
                                  _statColumn(isDark, count: '${library.folders.length}', label: 'FOLDERS'),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 24),

                      // 2. Audio Engine & Equalizer
                      Text(
                        'AUDIO HARDWARE & ENGINE',
                        style: NothingTypography.monoLabel(isDark: isDark),
                      ),
                      const SizedBox(height: 8),
                      NothingCard(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          children: [
                            // Equalizer Tile
                            InkWell(
                              onTap: () {
                                HapticFeedback.lightImpact();
                                EqualizerSheet.show(context);
                              },
                              borderRadius: BorderRadius.circular(NothingColors.radiusSm),
                              child: Padding(
                                padding: const EdgeInsets.symmetric(vertical: 6.0),
                                child: Row(
                                  children: [
                                    Icon(
                                      Icons.tune,
                                      size: 22,
                                      color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                    ),
                                    const SizedBox(width: 14),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            'EQUALIZER',
                                            style: TextStyle(
                                              fontFamily: NothingTypography.fontMono,
                                              fontSize: 13,
                                              fontWeight: FontWeight.w600,
                                              letterSpacing: 0.8,
                                              color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                            ),
                                          ),
                                          const SizedBox(height: 2),
                                          Text(
                                            'Pick your IEM · measured correction',
                                            style: TextStyle(
                                              fontFamily: NothingTypography.fontUi,
                                              fontSize: 12,
                                              color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                    Icon(
                                      Icons.chevron_right,
                                      size: 18,
                                      color: isDark ? NothingColors.darkMuted : NothingColors.lightMuted,
                                    ),
                                  ],
                                ),
                              ),
                            ),
                            Divider(
                              height: 20,
                              color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
                            ),
                            // Lossless Decoder Tile
                            Padding(
                              padding: const EdgeInsets.symmetric(vertical: 6.0),
                              child: Row(
                                children: [
                                  Icon(
                                    Icons.graphic_eq,
                                    size: 22,
                                    color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                  ),
                                  const SizedBox(width: 14),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          'LOSSLESS ENGINE',
                                          style: TextStyle(
                                            fontFamily: NothingTypography.fontMono,
                                            fontSize: 13,
                                            fontWeight: FontWeight.w600,
                                            letterSpacing: 0.8,
                                            color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                          ),
                                        ),
                                        const SizedBox(height: 2),
                                        Text(
                                          'FLAC, OPUS, WAV 24-bit Output',
                                          style: TextStyle(
                                            fontFamily: NothingTypography.fontUi,
                                            fontSize: 12,
                                            color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                  NothingStatusBadge(
                                    label: 'BIT-PERFECT',
                                    type: BadgeType.flacHiRes,
                                    showDot: true,
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 24),

                      // 3. Appearance & Theme
                      Text(
                        'APPEARANCE',
                        style: NothingTypography.monoLabel(isDark: isDark),
                      ),
                      const SizedBox(height: 8),
                      NothingCard(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'COLOR THEME',
                              style: TextStyle(
                                fontFamily: NothingTypography.fontMono,
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                letterSpacing: 0.8,
                                color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                              ),
                            ),
                            const SizedBox(height: 12),
                            Row(
                              children: [
                                Expanded(
                                  child: _themeButton(
                                    context,
                                    isDark: isDark,
                                    title: 'DARK',
                                    mode: ThemeMode.dark,
                                    isSelected: themeProvider.themeMode == ThemeMode.dark,
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: _themeButton(
                                    context,
                                    isDark: isDark,
                                    title: 'LIGHT',
                                    mode: ThemeMode.light,
                                    isSelected: themeProvider.themeMode == ThemeMode.light,
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: _themeButton(
                                    context,
                                    isDark: isDark,
                                    title: 'AUTO',
                                    mode: ThemeMode.system,
                                    isSelected: themeProvider.themeMode == ThemeMode.system,
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 24),

                      // Player look & behaviour
                      Text(
                        'PLAYER',
                        style: NothingTypography.monoLabel(isDark: isDark),
                      ),
                      const SizedBox(height: 8),
                      const PlayerSettingsCard(),
                      const SizedBox(height: 24),

                      // Stream the library to a laptop on the same Wi-Fi
                      Text(
                        'WEB STREAM',
                        style: NothingTypography.monoLabel(isDark: isDark),
                      ),
                      const SizedBox(height: 8),
                      const WebStreamCard(),
                      const SizedBox(height: 24),

                      // 4. Storage & Rescan
                      Text(
                        'STORAGE & PERMISSIONS',
                        style: NothingTypography.monoLabel(isDark: isDark),
                      ),
                      const SizedBox(height: 8),
                      NothingCard(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          children: [
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      'MEDIA PERMISSION',
                                      style: TextStyle(
                                        fontFamily: NothingTypography.fontMono,
                                        fontSize: 12,
                                        fontWeight: FontWeight.w600,
                                        letterSpacing: 0.8,
                                        color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                      ),
                                    ),
                                    const SizedBox(height: 2),
                                    Text(
                                      library.isPermissionGranted ? 'Storage access granted' : 'Permission required',
                                      style: TextStyle(
                                        fontFamily: NothingTypography.fontUi,
                                        fontSize: 11,
                                        color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                                      ),
                                    ),
                                  ],
                                ),
                                NothingStatusBadge(
                                  label: library.isPermissionGranted ? 'GRANTED' : 'DENIED',
                                  type: library.isPermissionGranted ? BadgeType.success : BadgeType.signalRed,
                                  showDot: true,
                                ),
                              ],
                            ),
                            Divider(height: 28, color: isDark ? NothingColors.darkLine : NothingColors.lightLine),
                            Row(
                              children: [
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        'ONLINE ARTWORK & LYRICS',
                                        style: TextStyle(
                                          fontFamily: NothingTypography.fontMono,
                                          fontSize: 12,
                                          fontWeight: FontWeight.w600,
                                          letterSpacing: 0.8,
                                          color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                        ),
                                      ),
                                      const SizedBox(height: 2),
                                      Text(
                                        'Missing covers from iTunes, lyrics from LRCLIB (sends title & artist)',
                                        style: TextStyle(
                                          fontFamily: NothingTypography.fontUi,
                                          fontSize: 11,
                                          color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Switch(
                                  key: const Key('online_artwork_switch'),
                                  value: library.onlineArtwork,
                                  activeThumbColor: NothingColors.darkDisplay,
                                  activeTrackColor: NothingColors.signalRed,
                                  onChanged: (v) {
                                    HapticFeedback.selectionClick();
                                    library.setOnlineArtwork(v);
                                  },
                                ),
                              ],
                            ),
                            const SizedBox(height: 16),
                            NothingButton(
                              text: 'RESCAN MUSIC STORAGE',
                              variant: NothingButtonVariant.outline,
                              onPressed: () {
                                HapticFeedback.mediumImpact();
                                library.loadLibrary(isRescan: true);
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(
                                    backgroundColor: isDark ? NothingColors.darkRaised : NothingColors.lightRaised,
                                    content: Text(
                                      'SCANNING MUSIC STORAGE...',
                                      style: TextStyle(
                                        fontFamily: NothingTypography.fontMono,
                                        fontSize: 12,
                                        color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                                      ),
                                    ),
                                    duration: const Duration(seconds: 2),
                                  ),
                                );
                              },
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 24),

                      // 5. About Lyro
                      Text(
                        'ABOUT',
                        style: NothingTypography.monoLabel(isDark: isDark),
                      ),
                      const SizedBox(height: 8),
                      NothingCard(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          children: [
                            _aboutRow(isDark, label: 'APPLICATION', value: AppConstants.appName),
                            Divider(height: 16, color: isDark ? NothingColors.darkLine : NothingColors.lightLine),
                            _aboutRow(isDark, label: 'VERSION', value: AppConstants.appVersion),
                            Divider(height: 16, color: isDark ? NothingColors.darkLine : NothingColors.lightLine),
                            InkWell(
                              key: const Key('privacy_policy_link'),
                              onTap: () => launchUrl(
                                Uri.parse(AppConstants.privacyPolicyUrl),
                                mode: LaunchMode.externalApplication,
                              ),
                              child: _aboutRow(isDark, label: 'PRIVACY POLICY', value: 'OPEN ↗'),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _statColumn(bool isDark, {required String count, required String label}) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          count,
          style: NothingTypography.dottedBold(
            isDark: isDark,
            fontSize: 20,
            letterSpacing: 0.8,
            color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: TextStyle(
            fontFamily: NothingTypography.fontMono,
            fontSize: 9,
            letterSpacing: 1.0,
            color: isDark ? NothingColors.darkMuted : NothingColors.lightMuted,
          ),
        ),
      ],
    );
  }

  Widget _divider(bool isDark) {
    return Container(
      width: 1,
      height: 28,
      color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
    );
  }

  Widget _themeButton(
    BuildContext context, {
    required bool isDark,
    required String title,
    required ThemeMode mode,
    required bool isSelected,
  }) {
    return GestureDetector(
      onTap: () {
        HapticFeedback.lightImpact();
        context.read<ThemeProvider>().setThemeMode(mode);
      },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          color: isSelected
              ? (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay)
              : (isDark ? NothingColors.darkRaised : NothingColors.lightRaised),
          borderRadius: BorderRadius.circular(NothingColors.radiusSm),
          border: Border.all(
            color: isSelected
                ? NothingColors.signalRed
                : (isDark ? NothingColors.darkLine : NothingColors.lightLine),
            width: isSelected ? 1.5 : 1.0,
          ),
        ),
        child: Center(
          child: Text(
            title,
            style: TextStyle(
              fontFamily: NothingTypography.fontMono,
              fontSize: 11,
              fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
              letterSpacing: 1.0,
              color: isSelected
                  ? (isDark ? NothingColors.lightDisplay : NothingColors.darkDisplay)
                  : (isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary),
            ),
          ),
        ),
      ),
    );
  }

  Widget _aboutRow(bool isDark, {required String label, required String value}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: TextStyle(
            fontFamily: NothingTypography.fontMono,
            fontSize: 11,
            color: isDark ? NothingColors.darkMuted : NothingColors.lightMuted,
          ),
        ),
        Text(
          value,
          style: TextStyle(
            fontFamily: NothingTypography.fontMono,
            fontSize: 11,
            fontWeight: FontWeight.w600,
            color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
          ),
        ),
      ],
    );
  }
}
