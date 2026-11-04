import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../data/models/song_model.dart';
import '../../player/providers/player_provider.dart';
import '../../player/widgets/track_artwork.dart';
import '../../../shared/widgets/nothing_status_badge.dart';
import '../../../shared/widgets/dot_toast.dart';
import '../../playlists/widgets/add_to_playlist_sheet.dart';

class SongTile extends StatelessWidget {
  final Song song;
  final List<Song> playlist;

  /// Shown as "Playing from" in the queue, e.g. "Folder · Downloads".
  final String? source;
  final VoidCallback? onTap;
  final bool showArtwork;
  final bool showFormatBadge;

  /// Shown under the duration, e.g. the play count in Most played.
  final Widget? durationCaption;

  const SongTile({
    super.key,
    required this.song,
    required this.playlist,
    this.source,
    this.onTap,
    this.showArtwork = true,
    this.showFormatBadge = true,
    this.durationCaption,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final (isCurrent, isPlaying) = context.select<PlayerProvider, (bool, bool)>(
      (p) {
        final current = p.currentSong?.id == song.id;
        return (current, current && p.isPlaying);
      },
    );

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap ?? () => context.read<PlayerProvider>().playSong(song, playlist, source: source),
        onLongPress: () => showOptions(context),
        splashColor: (isDark ? Colors.white : Colors.black).withValues(alpha: 0.06),
        highlightColor: (isDark ? Colors.white : Colors.black).withValues(alpha: 0.04),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 10.0),
          decoration: BoxDecoration(
            border: Border(
              bottom: BorderSide(
                color: isDark
                    ? NothingColors.darkLine.withValues(alpha: 0.4)
                    : NothingColors.lightLine.withValues(alpha: 0.6),
                width: 1.0,
              ),
            ),
          ),
          child: Row(
            children: [
              if (showArtwork) ...[
                Stack(
                  alignment: Alignment.center,
                  children: [
                    TrackArtwork(
                      songId: song.id,
                      title: song.title,
                      size: 46.0,
                    ),
                    if (isCurrent)
                      Container(
                        width: 46.0,
                        height: 46.0,
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.55),
                          borderRadius: BorderRadius.circular(NothingColors.radiusSm),
                        ),
                        child: Center(
                          child: Icon(
                            isPlaying ? Icons.equalizer : Icons.pause,
                            color: NothingColors.darkAccentText,
                            size: 20,
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(width: 14),
              ],
              // Track Details
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      children: [
                        if (isCurrent) ...[
                          Container(
                            width: 6,
                            height: 6,
                            margin: const EdgeInsets.only(right: 6),
                            decoration: const BoxDecoration(
                              color: NothingColors.darkAccent,
                              shape: BoxShape.circle,
                            ),
                          ),
                        ],
                        Expanded(
                          child: Text(
                            song.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontFamily: NothingTypography.fontUi,
                              fontSize: 14,
                              fontWeight: isCurrent ? FontWeight.w600 : FontWeight.w500,
                              color: isCurrent
                                  ? (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay)
                                  : (isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 3),
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            song.artist,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontFamily: NothingTypography.fontUi,
                              fontSize: 12,
                              color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                            ),
                          ),
                        ),
                        if (showFormatBadge) ...[
                          const SizedBox(width: 6),
                          NothingStatusBadge(
                            label: song.format,
                            type: song.isFlac ? BadgeType.flacHiRes : BadgeType.neutral,
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              // Duration
              Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    song.formattedDuration,
                    style: TextStyle(
                      fontFamily: NothingTypography.fontMono,
                      fontSize: 11,
                      color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                    ),
                  ),
                  ?durationCaption,
                ],
              ),
              // More options
              IconButton(
                icon: Icon(
                  Icons.more_vert,
                  size: 18,
                  color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                ),
                onPressed: () => showOptions(context),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Song menu (favorite, play now / next, queue, playlist, info); also used
  /// by Home's Quick picks.
  void showOptions(BuildContext context) {
    final player = context.read<PlayerProvider>();
    final isDark = context.isDark;
    final isFav = player.isFavorite(song.id);

    showModalBottomSheet(
      context: context,
      backgroundColor: isDark ? NothingColors.darkSurface : NothingColors.lightSurface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(NothingColors.radiusMd)),
      ),
      builder: (ctx) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 16.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20.0, vertical: 8.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        song.title.toUpperCase(),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontFamily: NothingTypography.fontMono,
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '${song.artist} • ${song.album}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: NothingTypography.bodySmall(isDark: isDark),
                      ),
                    ],
                  ),
                ),
                Divider(color: isDark ? NothingColors.darkLine : NothingColors.lightLine),
                ListTile(
                  leading: Icon(
                    isFav ? Icons.favorite : Icons.favorite_border,
                    color: isFav
                        ? NothingColors.darkAccentText
                        : (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay),
                  ),
                  title: Text(
                    isFav ? 'REMOVE FROM FAVORITES' : 'ADD TO FAVORITES',
                    style: NothingTypography.monoLabel(isDark: isDark),
                  ),
                  onTap: () {
                    player.toggleFavorite(song.id);
                    Navigator.pop(ctx);
                  },
                ),
                ListTile(
                  leading: Icon(
                    Icons.play_circle_outline,
                    color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                  ),
                  title: Text('PLAY NOW', style: NothingTypography.monoLabel(isDark: isDark)),
                  onTap: () {
                    Navigator.pop(ctx);
                    player.playSong(song, playlist, source: source);
                  },
                ),
                ListTile(
                  key: const Key('song_play_next'),
                  leading: Icon(
                    Icons.playlist_play,
                    color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                  ),
                  title: Text('PLAY NEXT', style: NothingTypography.monoLabel(isDark: isDark)),
                  onTap: () async {
                    Navigator.pop(ctx);
                    await player.playNext(song);
                    if (context.mounted) showDotToast(context, 'Queued · next');
                  },
                ),
                ListTile(
                  key: const Key('song_add_to_queue'),
                  leading: Icon(
                    Icons.queue_music,
                    color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                  ),
                  title: Text('ADD TO QUEUE', style: NothingTypography.monoLabel(isDark: isDark)),
                  onTap: () async {
                    Navigator.pop(ctx);
                    final ahead = await player.addToQueue(song);
                    if (context.mounted) {
                      showDotToast(context, ahead <= 1 ? 'Queued · next' : 'Queued · ${ordinal(ahead)}');
                    }
                  },
                ),
                ListTile(
                  key: const Key('song_add_to_playlist'),
                  leading: Icon(
                    Icons.playlist_add,
                    color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                  ),
                  title: Text('ADD TO PLAYLIST', style: NothingTypography.monoLabel(isDark: isDark)),
                  onTap: () {
                    Navigator.pop(ctx);
                    AddToPlaylistSheet.show(context, [song]);
                  },
                ),
                ListTile(
                  leading: Icon(
                    Icons.info_outline,
                    color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
                  ),
                  title: Text('TRACK INFO', style: NothingTypography.monoLabel(isDark: isDark)),
                  subtitle: Text(
                    'Path: ${song.path}\nFormat: ${song.format} • Size: ${((song.size ?? 0) / (1024 * 1024)).toStringAsFixed(2)} MB',
                    style: TextStyle(
                      fontFamily: NothingTypography.fontMono,
                      fontSize: 10,
                      color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                    ),
                  ),
                  onTap: () => Navigator.pop(ctx),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
