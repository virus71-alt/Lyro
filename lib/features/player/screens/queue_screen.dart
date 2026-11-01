import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_dot_painter.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../shared/widgets/nothing_button.dart';
import '../../../shared/widgets/nothing_dialog.dart';
import '../providers/player_provider.dart';
import '../widgets/track_artwork.dart';

class QueueScreen extends StatelessWidget {
  const QueueScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final player = context.watch<PlayerProvider>();
    final queue = player.queue;
    final currentIndex = player.queueIndex;

    return Scaffold(
      backgroundColor: isDark ? NothingColors.darkBg : NothingColors.lightBg,
      appBar: AppBar(
        title: Text(
          'PLAYBACK QUEUE',
          style: NothingTypography.displayTitle(isDark: isDark, fontSize: 16),
        ),
        actions: [
          if (queue.isNotEmpty)
            IconButton(
              icon: Icon(
                Icons.delete_outline,
                size: 20,
                color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
              ),
              onPressed: () => _confirmClearQueue(context, player),
              tooltip: 'Clear Queue',
            ),
        ],
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
          if (queue.isEmpty)
            Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.queue_music,
                    size: 48,
                    color: isDark ? NothingColors.darkLine2 : NothingColors.lightLine2,
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'QUEUE IS EMPTY',
                    style: NothingTypography.monoLabel(isDark: isDark),
                  ),
                ],
              ),
            )
          else
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '${queue.length} TRACKS IN QUEUE',
                        style: NothingTypography.monoLabel(isDark: isDark),
                      ),
                      Row(
                        children: [
                          NothingButton(
                            text: 'SHUFFLE',
                            variant: NothingButtonVariant.pill,
                            isSelected: player.isShuffle,
                            onPressed: () => player.toggleShuffle(),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const Divider(height: 1),
                Expanded(
                  child: ReorderableListView.builder(
                    itemCount: queue.length,
                    onReorderItem: (oldIndex, newIndex) {
                      player.reorderQueue(oldIndex, newIndex);
                    },
                    itemBuilder: (context, index) {
                      final song = queue[index];
                      final isCurrent = index == currentIndex;

                      return Container(
                        key: ValueKey('${song.id}_$index'),
                        color: isCurrent
                            ? (isDark
                                ? NothingColors.darkRaised.withValues(alpha: 0.5)
                                : NothingColors.lightRaised.withValues(alpha: 0.5))
                            : Colors.transparent,
                        child: ListTile(
                          leading: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                '${index + 1}'.padLeft(2, '0'),
                                style: TextStyle(
                                  fontFamily: NothingTypography.fontMono,
                                  fontSize: 11,
                                  color: isCurrent
                                      ? NothingColors.darkAccentText
                                      : (isDark ? NothingColors.darkMuted : NothingColors.lightMuted),
                                ),
                              ),
                              const SizedBox(width: 10),
                              TrackArtwork(
                                songId: song.id,
                                title: song.title,
                                size: 38,
                              ),
                            ],
                          ),
                          title: Row(
                            children: [
                              if (isCurrent) ...[
                                Container(
                                  width: 5,
                                  height: 5,
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
                                    fontSize: 13,
                                    fontWeight: isCurrent ? FontWeight.w600 : FontWeight.w400,
                                    color: isCurrent
                                        ? (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay)
                                        : (isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary),
                                  ),
                                ),
                              ),
                            ],
                          ),
                          subtitle: Text(
                            song.artist,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontFamily: NothingTypography.fontUi,
                              fontSize: 11,
                              color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                            ),
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                icon: Icon(
                                  Icons.close,
                                  size: 16,
                                  color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                                ),
                                onPressed: () => player.removeQueueItem(index),
                              ),
                              ReorderableDragStartListener(
                                index: index,
                                child: Icon(
                                  Icons.drag_handle,
                                  size: 20,
                                  color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
                                ),
                              ),
                            ],
                          ),
                          onTap: () => player.skipToQueueItem(index),
                        ),
                      );
                    },
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }

  void _confirmClearQueue(BuildContext context, PlayerProvider player) async {
    final confirmed = await NothingDialog.show(
      context: context,
      title: 'CLEAR QUEUE',
      content: 'Do you want to remove all upcoming tracks from the playback queue?',
      confirmText: 'CLEAR',
      cancelText: 'CANCEL',
      isDestructive: true,
    );

    if (confirmed == true) {
      player.clearQueue();
    }
  }
}
