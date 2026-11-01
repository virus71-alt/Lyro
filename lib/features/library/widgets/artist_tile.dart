import 'package:flutter/material.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../core/utils/formatters.dart';
import '../../../data/models/artist_model.dart';

class ArtistTile extends StatelessWidget {
  final Artist artist;
  final VoidCallback onTap;

  const ArtistTile({
    super.key,
    required this.artist,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
          decoration: BoxDecoration(
            border: Border(
              bottom: BorderSide(
                color: isDark
                    ? NothingColors.darkLine.withValues(alpha: 0.5)
                    : NothingColors.lightLine.withValues(alpha: 0.6),
                width: 1.0,
              ),
            ),
          ),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: isDark ? NothingColors.darkRaised : NothingColors.lightRaised,
                  border: Border.all(
                    color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
                    width: 1.0,
                  ),
                ),
                child: Center(
                  child: Text(
                    artist.artist.isNotEmpty ? artist.artist[0].toUpperCase() : 'A',
                    style: NothingTypography.dottedBold(
                      isDark: isDark,
                      fontSize: 16,
                      color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      artist.artist,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontFamily: NothingTypography.fontUi,
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      '${artist.numberOfAlbums} ALBUMS • ${Formatters.formatTrackCount(artist.numberOfTracks)}',
                      style: TextStyle(
                        fontFamily: NothingTypography.fontMono,
                        fontSize: 10,
                        color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                      ),
                    ),
                  ],
                ),
              ),
              Icon(
                Icons.chevron_right,
                size: 18,
                color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
