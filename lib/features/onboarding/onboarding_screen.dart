import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../core/settings/app_settings_provider.dart';
import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_dot_painter.dart';
import '../../core/theme/nothing_typography.dart';
import '../../shared/widgets/dot_matrix.dart';
import '../library/providers/library_provider.dart';

/// First launch: what Lyro asks for and why, before Android's prompts.
class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  bool _busy = false;

  Future<void> _start() async {
    setState(() => _busy = true);
    HapticFeedback.mediumImpact();
    final library = context.read<LibraryProvider>();
    final settings = context.read<AppSettingsProvider>();
    await library.requestPermissionAndLoad();
    await settings.completeOnboarding();
  }

  @override
  Widget build(BuildContext context) {
    final reduce = context.watch<AppSettingsProvider>().motionReduced(context);
    return Scaffold(
      backgroundColor: NothingColors.darkBg,
      body: Stack(
        fit: StackFit.expand,
        children: [
          Positioned.fill(
            child: CustomPaint(painter: NothingDotPainter(isDark: true, spacing: 24, dotRadius: 0.8)),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(28, 40, 28, 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Image.asset('assets/icon/mark.png', width: 44, height: 44),
                      const SizedBox(width: 14),
                      DotMatrixLogo(isPlaying: true, still: reduce, dotPitch: 5),
                    ],
                  ),
                  const SizedBox(height: 22),
                  const Text(
                    'Pure sound.\nMinimal form.',
                    style: TextStyle(
                      fontFamily: NothingTypography.fontUi,
                      fontSize: 30,
                      fontWeight: FontWeight.w700,
                      height: 1.15,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    'An offline player for the music on this phone.',
                    style: NothingTypography.bodyMedium(isDark: true),
                  ),
                  const Spacer(),
                  Text('BEFORE WE START', style: NothingTypography.monoLabel(isDark: true)),
                  const SizedBox(height: 14),
                  const _Reason(
                    artId: DotEmptyState.note,
                    title: 'Music',
                    text: 'Needed to find and play the songs stored on this device.',
                  ),
                  const _Reason(
                    artId: DotEmptyState.spark,
                    title: 'Online · optional',
                    text: 'Missing covers and lyrics can be fetched by title & artist. Turn off any time in Profile.',
                  ),
                  const SizedBox(height: 24),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton(
                      key: const Key('onboarding_start'),
                      onPressed: _busy ? null : _start,
                      style: FilledButton.styleFrom(
                        backgroundColor: Colors.white,
                        foregroundColor: Colors.black,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(NothingColors.radiusPill)),
                      ),
                      child: Text(
                        _busy ? 'SCANNING…' : 'GET STARTED',
                        style: const TextStyle(
                          fontFamily: NothingTypography.fontMono,
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          letterSpacing: 1.6,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Reason extends StatelessWidget {
  final int artId;
  final String title;
  final String text;
  const _Reason({required this.artId, required this.title, required this.text});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          DotArt(artId: artId, size: 36),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title.toUpperCase(),
                  style: const TextStyle(
                    fontFamily: NothingTypography.fontMono,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 1,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 3),
                Text(text, style: NothingTypography.bodySmall(isDark: true)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
