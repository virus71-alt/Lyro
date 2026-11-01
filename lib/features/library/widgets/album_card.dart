import 'package:flutter/material.dart';
import 'package:on_audio_query/on_audio_query.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../core/utils/formatters.dart';
import '../../../data/models/album_model.dart';
import '../../player/widgets/track_artwork.dart';

class AlbumCard extends StatelessWidget {
  final Album album;
  final VoidCallback onTap;

  const AlbumCard({
    super.key,
    required this.album,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(NothingColors.radiusMd),
        child: Container(
          decoration: BoxDecoration(
            color: isDark ? NothingColors.darkGlass : NothingColors.lightGlass,
            borderRadius: BorderRadius.circular(NothingColors.radiusMd),
            border: Border.all(
              color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
              width: 1.0,
            ),
          ),
          padding: const EdgeInsets.all(10.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Center(
                  child: AspectRatio(
                    aspectRatio: 1.0,
                    child: TrackArtwork(
                      songId: album.songId ?? album.id,
                      title: album.album,
                      type: album.songId != null ? ArtworkType.AUDIO : ArtworkType.ALBUM,
                      size: double.infinity,
                      radius: NothingColors.radiusSm,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 10),
              Text(
                album.album,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontFamily: NothingTypography.fontUi,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                ),
              ),
              const SizedBox(height: 3),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Text(
                      album.artist,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontFamily: NothingTypography.fontUi,
                        fontSize: 11,
                        color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                      ),
                    ),
                  ),
                  Text(
                    Formatters.formatTrackCount(album.numOfSongs),
                    style: TextStyle(
                      fontFamily: NothingTypography.fontMono,
                      fontSize: 9,
                      color: isDark ? NothingColors.darkMuted : NothingColors.lightMuted,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

