import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/theme/nothing_colors.dart';
import '../../core/theme/nothing_typography.dart';
import '../../features/player/widgets/artwork_tint.dart';
import '../../features/player/widgets/track_artwork.dart';

/// Header for a song collection (album, artist, playlist, smart list):
/// artwork, title, subtitle and PLAY / SHUFFLE, on a gradient tinted from
/// the artwork's dominant colour.
class CollectionHeader extends StatelessWidget {
  final String title;
  final String subtitle;
  final String? kicker; // small mono label above the title
  final int? artworkSongId;
  final Widget? artwork; // overrides the track artwork (e.g. dot art)
  final VoidCallback? onPlay;
  final VoidCallback? onShuffle;
  final List<Widget> actions;

  const CollectionHeader({
    super.key,
    required this.title,
    required this.subtitle,
    this.kicker,
    this.artworkSongId,
    this.artwork,
    this.onPlay,
    this.onShuffle,
    this.actions = const [],
  });

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final display = dark ? NothingColors.darkDisplay : NothingColors.lightDisplay;
    final secondary = dark ? NothingColors.darkSecondary : NothingColors.lightSecondary;
    final bg = dark ? NothingColors.darkBg : NothingColors.lightBg;

    Widget body(Color? tint) => Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                tint == null
                    ? bg
                    : (dark
                        ? ArtworkTint.background(tint, lift: 0.04)
                        : Color.lerp(tint, Colors.white, 0.82)!),
                bg,
              ],
            ),
          ),
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              SizedBox(
                width: 112,
                height: 112,
                child: artwork ??
                    (artworkSongId == null
                        ? const SizedBox()
                        : TrackArtwork(songId: artworkSongId!, title: title, size: 112, radius: 10)),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (kicker != null)
                      Text(kicker!.toUpperCase(), style: NothingTypography.monoLabel(isDark: dark)),
                    const SizedBox(height: 4),
                    Text(
                      title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontFamily: NothingTypography.fontUi,
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                        height: 1.15,
                        color: display,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      subtitle.toUpperCase(),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontFamily: NothingTypography.fontMono,
                        fontSize: 10,
                        letterSpacing: 1.0,
                        color: secondary,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        if (onPlay != null)
                          _pill(context, 'PLAY', Icons.play_arrow, onPlay!, filled: true, dark: dark),
                        if (onShuffle != null) ...[
                          const SizedBox(width: 8),
                          _pill(context, 'SHUFFLE', Icons.shuffle, onShuffle!, filled: false, dark: dark),
                        ],
                        const Spacer(),
                        ...actions,
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        );

    return artworkSongId == null
        ? body(null)
        : ArtworkTintBuilder(songId: artworkSongId!, builder: (context, tint) => body(tint));
  }

  Widget _pill(BuildContext context, String label, IconData icon, VoidCallback onTap,
      {required bool filled, required bool dark}) {
    final fg = filled ? (dark ? Colors.black : Colors.white) : (dark ? Colors.white : Colors.black);
    return Material(
      color: filled
          ? (dark ? NothingColors.darkDisplay : NothingColors.lightDisplay)
          : (dark ? Colors.white : Colors.black).withValues(alpha: 0.08),
      borderRadius: BorderRadius.circular(NothingColors.radiusPill),
      child: InkWell(
        borderRadius: BorderRadius.circular(NothingColors.radiusPill),
        onTap: () {
          HapticFeedback.lightImpact();
          onTap();
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 16, color: fg),
              const SizedBox(width: 6),
              Text(
                label,
                style: TextStyle(
                  fontFamily: NothingTypography.fontMono,
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  letterSpacing: 1.2,
                  color: fg,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Fades a header's background from the artwork's colour into the page.
class ArtworkTintBackdrop extends StatelessWidget {
  final int? songId;
  final Widget child;
  const ArtworkTintBackdrop({super.key, required this.songId, required this.child});

  @override
  Widget build(BuildContext context) {
    final id = songId;
    if (id == null || id <= 0) return child;
    final dark = Theme.of(context).brightness == Brightness.dark;
    final bg = dark ? NothingColors.darkBg : NothingColors.lightBg;
    return ArtworkTintBuilder(
      songId: id,
      builder: (context, tint) => DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              tint == null
                  ? bg.withValues(alpha: 0)
                  : (dark ? ArtworkTint.background(tint, lift: 0.04) : Color.lerp(tint, Colors.white, 0.82)!),
              bg.withValues(alpha: 0),
            ],
          ),
        ),
        child: child,
      ),
    );
  }
}
