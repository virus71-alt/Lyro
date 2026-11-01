import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../shared/widgets/dot_matrix.dart';
import '../../playlists/providers/playlist_provider.dart';
import '../../playlists/screens/playlists_screen.dart';
import '../screens/smart_list_screen.dart';

/// Home row: Playlists entry plus the three smart lists, as dot-art cards.
class HomeShortcuts extends StatelessWidget {
  const HomeShortcuts({super.key});

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final playlists = context.watch<PlaylistProvider>().playlists.length;

    void open(Widget screen) {
      HapticFeedback.lightImpact();
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => screen));
    }

    return SizedBox(
      height: 116,
      child: ListView(
        scrollDirection: Axis.horizontal,
        children: [
          _card(
            key: const Key('home_playlists'),
            dark: dark,
            artId: DotEmptyState.note,
            title: 'Playlists',
            detail: playlists == 0 ? 'Create one' : '$playlists saved',
            accent: true,
            onTap: () => open(const PlaylistsScreen()),
          ),
          for (final kind in SmartList.values)
            _card(
              key: Key('home_smart_${kind.name}'),
              dark: dark,
              artId: kind.artId,
              title: kind.title,
              detail: 'Smart list',
              onTap: () => open(SmartListScreen(kind: kind)),
            ),
        ],
      ),
    );
  }

  Widget _card({
    required Key key,
    required bool dark,
    required int artId,
    required String title,
    required String detail,
    required VoidCallback onTap,
    bool accent = false,
  }) {
    return Padding(
      key: key,
      padding: const EdgeInsets.only(right: 10),
      child: Material(
        color: dark ? NothingColors.darkRaised : NothingColors.lightRaised,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(NothingColors.radiusSm),
          side: BorderSide(
            color: accent ? NothingColors.signalRed.withValues(alpha: 0.6) : (dark ? NothingColors.darkLine : NothingColors.lightLine),
          ),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(NothingColors.radiusSm),
          onTap: onTap,
          child: SizedBox(
            width: 132,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  DotArt(artId: artId, size: 40),
                  const Spacer(),
                  Text(
                    title.toUpperCase(),
                    maxLines: 2,
                    style: TextStyle(
                      fontFamily: NothingTypography.fontMono,
                      fontSize: 10.5,
                      fontWeight: FontWeight.w600,
                      letterSpacing: 0.8,
                      height: 1.2,
                      color: dark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    detail,
                    style: TextStyle(
                      fontFamily: NothingTypography.fontUi,
                      fontSize: 11,
                      color: dark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
