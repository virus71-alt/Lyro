import 'package:flutter/foundation.dart';
import '../../../shared/widgets/dot_matrix.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../data/models/song_model.dart';
import '../../../shared/widgets/dot_toast.dart';
import '../../../shared/widgets/nothing_dialog.dart';
import '../../playlists/providers/playlist_provider.dart';
import '../../playlists/widgets/add_to_playlist_sheet.dart';
import '../providers/player_provider.dart';
import 'track_artwork.dart';

enum _QueueFilter { all, upNext, played }

/// Pull-up queue panel shown over Now Playing.
///
/// Only the header (grabber, "Playing from" and the filter chips) moves the
/// sheet, reported through [onDragUpdate]/[onDragEnd]; the list just scrolls,
/// so scrolling, reordering or swiping a song never closes the queue by
/// accident.
class QueueSheet extends StatefulWidget {
  final Color background;
  final ValueChanged<double> onDragUpdate; // dy in px, positive = down
  final ValueChanged<double> onDragEnd; // velocity in px/s, positive = down

  /// Notifies each time the sheet is opened; the list then scrolls the
  /// current track into view.
  final ValueListenable<int>? openSignal;

  const QueueSheet({
    super.key,
    required this.background,
    required this.onDragUpdate,
    required this.onDragEnd,
    this.openSignal,
  });

  @override
  State<QueueSheet> createState() => _QueueSheetState();
}

class _QueueSheetState extends State<QueueSheet> {
  static const double _rowHeight = 64;

  final ScrollController _scroll = ScrollController();
  final TextEditingController _search = TextEditingController();
  _QueueFilter _filter = _QueueFilter.all;
  bool _searching = false;
  String _query = '';

  @override
  void initState() {
    super.initState();
    widget.openSignal?.addListener(_scrollToCurrent);
    _scrollToCurrent();
  }

  @override
  void didUpdateWidget(covariant QueueSheet oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.openSignal != oldWidget.openSignal) {
      oldWidget.openSignal?.removeListener(_scrollToCurrent);
      widget.openSignal?.addListener(_scrollToCurrent);
    }
  }

  /// Puts the current track near the top, like the reference.
  void _scrollToCurrent() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_scroll.hasClients) return;
      final index = context.read<PlayerProvider>().queueIndex;
      final target = ((index - 1) * _rowHeight).clamp(
        0.0,
        _scroll.position.maxScrollExtent,
      );
      _scroll.jumpTo(target);
    });
  }

  @override
  void dispose() {
    widget.openSignal?.removeListener(_scrollToCurrent);
    _scroll.dispose();
    _search.dispose();
    super.dispose();
  }

  void _toggleSearch() {
    HapticFeedback.selectionClick();
    setState(() {
      _searching = !_searching;
      _search.clear();
      _query = '';
    });
  }

  bool _matches(Song s, String q) =>
      s.title.toLowerCase().contains(q) ||
      s.artist.toLowerCase().contains(q) ||
      s.album.toLowerCase().contains(q);

  String _totalTime(List<Song> songs) {
    final ms = songs.fold<int>(0, (sum, s) => sum + s.duration);
    final minutes = (ms / 60000).round();
    if (minutes < 60) return '$minutes min';
    return '${minutes ~/ 60} h ${minutes % 60} min';
  }

  @override
  Widget build(BuildContext context) {
    final fg = _fgOf(context);
    final player = context.watch<PlayerProvider>();
    final queue = player.queue;
    final current = player.queueIndex;

    final inFilter = switch (_filter) {
      _QueueFilter.all => [for (var i = 0; i < queue.length; i++) i],
      _QueueFilter.upNext => [
        for (var i = current + 1; i < queue.length; i++) i,
      ],
      _QueueFilter.played => [
        for (var i = 0; i < current && i < queue.length; i++) i,
      ],
    };
    final q = _query.trim().toLowerCase();
    final indices = q.isEmpty ? inFilter : [for (final i in inFilter) if (_matches(queue[i], q)) i];
    // Row positions only match the queue when nothing is filtered out.
    final reorderable = _filter == _QueueFilter.all && q.isEmpty;

    return Material(
      color: widget.background,
      borderRadius: const BorderRadius.vertical(top: Radius.circular(22)),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Grabber, header and chips: drag here to move the sheet
          GestureDetector(
            behavior: HitTestBehavior.opaque,
            onVerticalDragUpdate: (d) => widget.onDragUpdate(d.delta.dy),
            onVerticalDragEnd: (d) => widget.onDragEnd(d.primaryVelocity ?? 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    margin: const EdgeInsets.only(top: 10, bottom: 14),
                    width: 36,
                    height: 4,
                    decoration: BoxDecoration(
                      color: fg.withValues(alpha: 0.28),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 0, 16, 14),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Playing from',
                              style: TextStyle(
                                fontFamily: NothingTypography.fontUi,
                                fontSize: 13,
                                color: fg.withValues(alpha: 0.6),
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              player.queueSource,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontFamily: NothingTypography.fontUi,
                                fontSize: 15,
                                fontWeight: FontWeight.w600,
                                color: fg,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '${queue.length} tracks · ${_totalTime(queue)}',
                              style: TextStyle(
                                fontFamily: NothingTypography.fontMono,
                                fontSize: 11,
                                letterSpacing: 0.6,
                                color: fg.withValues(alpha: 0.5),
                              ),
                            ),
                          ],
                        ),
                      ),
                      if (queue.isNotEmpty) ...[
                        IconButton(
                          key: const Key('queue_search'),
                          tooltip: _searching ? 'Close search' : 'Search queue',
                          icon: Icon(
                            _searching ? Icons.close : Icons.search,
                            color: fg.withValues(alpha: 0.7),
                          ),
                          onPressed: _toggleSearch,
                        ),
                        IconButton(
                          key: const Key('queue_clear'),
                          tooltip: 'Clear upcoming',
                          icon: Icon(
                            Icons.playlist_remove,
                            color: fg.withValues(alpha: 0.7),
                          ),
                          onPressed: () => _confirmClear(context, player),
                        ),
                        _PillButton(
                          key: const Key('queue_save'),
                          icon: Icons.playlist_add,
                          label: 'Save',
                          onTap: () => _saveAsPlaylist(context, player),
                        ),
                      ],
                    ],
                  ),
                ),
                if (_searching)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
                    child: TextField(
                      key: const Key('queue_search_field'),
                      controller: _search,
                      autofocus: true,
                      textInputAction: TextInputAction.search,
                      cursorColor: NothingColors.signalRed,
                      onChanged: (v) => setState(() => _query = v),
                      style: TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 15, color: fg),
                      decoration: InputDecoration(
                        isDense: true,
                        hintText: 'Search this queue',
                        hintStyle: TextStyle(fontFamily: NothingTypography.fontUi, fontSize: 15, color: fg.withValues(alpha: 0.45)),
                        prefixIcon: Icon(Icons.search, size: 20, color: fg.withValues(alpha: 0.5)),
                        filled: true,
                        fillColor: fg.withValues(alpha: 0.08),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide.none,
                        ),
                      ),
                    ),
                  ),
                // Filter chips
                SizedBox(
                  height: 34,
                  child: ListView(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    children: [
                      for (final (f, label) in [
                        (_QueueFilter.all, 'All'),
                        (_QueueFilter.upNext, 'Up next'),
                        (_QueueFilter.played, 'Played'),
                      ])
                        Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: _Chip(
                            label: label,
                            selected: _filter == f,
                            onTap: () => setState(() => _filter = f),
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 10),
              ],
            ),
          ),

          Expanded(
            child: indices.isEmpty
                ? ListView(
                    controller: _scroll,
                    physics: const AlwaysScrollableScrollPhysics(
                      parent: ClampingScrollPhysics(),
                    ),
                    children: [
                      const SizedBox(height: 48),
                      DotEmptyState(artId: DotEmptyState.vinyl, title: queue.isEmpty ? 'Queue is empty' : (q.isEmpty ? 'Nothing here' : 'No matches'), hint: null),
                    ],
                  )
                : ReorderableListView.builder(
                    scrollController: _scroll,
                    physics: const AlwaysScrollableScrollPhysics(
                      parent: ClampingScrollPhysics(),
                    ),
                    buildDefaultDragHandles: false,
                    padding: const EdgeInsets.only(bottom: 24),
                    itemCount: indices.length,
                    onReorderItem: (oldIndex, newIndex) {
                      if (!reorderable) return;
                      HapticFeedback.selectionClick();
                      player.reorderQueue(oldIndex, newIndex);
                    },
                    proxyDecorator: (child, _, _) => Material(
                      color: fg.withValues(alpha: 0.1),
                      elevation: 6,
                      child: child,
                    ),
                    itemBuilder: (context, i) {
                      final index = indices[i];
                      final song = queue[index];
                      final priorityEnd = player.priorityEnd;
                      return _QueueRow(
                        key: ValueKey('queue_${song.id}_$index'),
                        song: song,
                        sectionTitle:
                            reorderable &&
                                index == priorityEnd &&
                                priorityEnd > 0
                            ? 'MORE FROM YOUR LIBRARY'
                            : null,
                        listIndex: i,
                        isCurrent: index == current,
                        isPlaying: player.isPlaying,
                        canReorder: reorderable,
                        onTap: () {
                          HapticFeedback.selectionClick();
                          player.skipToQueueItem(index);
                        },
                        onRemove: () {
                          player.removeQueueItem(index);
                          showDotToast(
                            context,
                            'Removed · ${song.title}',
                            actionLabel: 'Undo',
                            onAction: () => player.insertQueueItem(index, song),
                          );
                        },
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  /// Saves the whole queue as a new playlist.
  Future<void> _saveAsPlaylist(BuildContext context, PlayerProvider player) async {
    final name = await askPlaylistName(
      context,
      title: 'Save queue as playlist',
      initial: player.queueSource.contains('·')
          ? player.queueSource.split('·').last.trim()
          : 'My queue',
    );
    if (name == null || !context.mounted) return;
    final playlists = context.read<PlaylistProvider>();
    final p = await playlists.create(name, songs: player.queue);
    if (context.mounted) showDotToast(context, 'Saved · ${p.name}');
  }

  Future<void> _confirmClear(
    BuildContext context,
    PlayerProvider player,
  ) async {
    final confirmed = await NothingDialog.show(
      context: context,
      title: 'CLEAR QUEUE',
      content: 'Remove the upcoming tracks? Playback will continue with the rest of your library.',
      confirmText: 'CLEAR',
      cancelText: 'CANCEL',
      isDestructive: true,
    );
    if (confirmed == true) player.clearQueue();
  }
}

class _QueueRow extends StatelessWidget {
  final Song song;
  final int listIndex;
  final bool isCurrent;
  final bool isPlaying;
  final bool canReorder;
  final String? sectionTitle;
  final VoidCallback onTap;
  final VoidCallback onRemove;

  const _QueueRow({
    super.key,
    required this.song,
    this.sectionTitle,
    required this.listIndex,
    required this.isCurrent,
    required this.isPlaying,
    required this.canReorder,
    required this.onTap,
    required this.onRemove,
  });

  @override
  Widget build(BuildContext context) {
    final fg = _fgOf(context);
    final row = Material(
      color: isCurrent
          ? fg.withValues(alpha: 0.08)
          : Colors.transparent,
      child: InkWell(
        onTap: onTap,
        child: SizedBox(
          height: 64,
          child: Padding(
            padding: const EdgeInsets.only(left: 20, right: 8),
            child: Row(
              children: [
                SizedBox(
                  width: 48,
                  height: 48,
                  child: Stack(
                    children: [
                      TrackArtwork(
                        songId: song.id,
                        title: song.title,
                        size: 48,
                        radius: 4,
                      ),
                      if (isCurrent)
                        Container(
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.55),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          alignment: Alignment.center,
                          child: Icon(
                            isPlaying ? Icons.graphic_eq : Icons.pause,
                            size: 22,
                            color: NothingColors.signalRedBright,
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        song.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontFamily: NothingTypography.fontUi,
                          fontSize: 15,
                          fontWeight: isCurrent
                              ? FontWeight.w600
                              : FontWeight.w400,
                          color: fg,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        '${song.artist} • ${song.formattedDuration}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontFamily: NothingTypography.fontUi,
                          fontSize: 13,
                          color: fg.withValues(alpha: 0.6),
                        ),
                      ),
                    ],
                  ),
                ),
                if (canReorder)
                  ReorderableDragStartListener(
                    index: listIndex,
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Icon(
                        Icons.drag_handle,
                        size: 24,
                        color: fg.withValues(alpha: 0.75),
                      ),
                    ),
                  )
                else
                  const SizedBox(width: 12),
              ],
            ),
          ),
        ),
      ),
    );

    final title = sectionTitle;
    final content = title == null
        ? row
        : Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 18, 20, 8),
                child: Text(
                  title,
                  style: TextStyle(
                    fontFamily: NothingTypography.fontMono,
                    fontSize: 11,
                    fontWeight: FontWeight.w500,
                    letterSpacing: 1.4,
                    color: fg.withValues(alpha: 0.5),
                  ),
                ),
              ),
              row,
            ],
          );

    if (isCurrent) return content;

    // Swipe either way to remove from the queue
    return Dismissible(
      key: ValueKey('dismiss_${song.id}_$listIndex'),
      onDismissed: (_) {
        HapticFeedback.lightImpact();
        onRemove();
      },
      background: const _RemoveBackground(alignment: Alignment.centerLeft),
      secondaryBackground: const _RemoveBackground(
        alignment: Alignment.centerRight,
      ),
      child: content,
    );
  }
}

class _RemoveBackground extends StatelessWidget {
  final Alignment alignment;
  const _RemoveBackground({required this.alignment});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: NothingColors.signalRed.withValues(alpha: 0.85),
      alignment: alignment,
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: const Text(
        'REMOVE',
        style: TextStyle(
          fontFamily: NothingTypography.fontMono,
          fontSize: 11,
          fontWeight: FontWeight.w600,
          letterSpacing: 1.6,
          color: Colors.white,
        ),
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _Chip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final fg = _fgOf(context);
    final onFg = _onFgOf(context);
    return GestureDetector(
      onTap: () {
        HapticFeedback.selectionClick();
        onTap();
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 14),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: selected ? fg : fg.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontFamily: NothingTypography.fontUi,
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: selected ? onFg : fg,
          ),
        ),
      ),
    );
  }
}

class _PillButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _PillButton({
    super.key,
    required this.icon,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final fg = _fgOf(context);
    return Material(
      color: fg.withValues(alpha: 0.1),
      borderRadius: BorderRadius.circular(NothingColors.radiusPill),
      child: InkWell(
        borderRadius: BorderRadius.circular(NothingColors.radiusPill),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 9),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 18, color: fg),
              const SizedBox(width: 6),
              Text(
                label,
                style: TextStyle(
                  fontFamily: NothingTypography.fontUi,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
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

/// Compact player bar shown at the top while the queue is open.
class QueueMiniPlayerBar extends StatelessWidget {
  final Song song;
  final bool isPlaying;
  final VoidCallback onTap;
  final VoidCallback onPrevious;
  final VoidCallback onPlayPause;
  final VoidCallback onNext;

  const QueueMiniPlayerBar({
    super.key,
    required this.song,
    required this.isPlaying,
    required this.onTap,
    required this.onPrevious,
    required this.onPlayPause,
    required this.onNext,
  });

  @override
  Widget build(BuildContext context) {
    final fg = _fgOf(context);
    Widget control(
      IconData icon,
      double size,
      VoidCallback action,
      String tip,
    ) => IconButton(
      icon: Icon(icon, size: size, color: fg),
      tooltip: tip,
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
      visualDensity: VisualDensity.compact,
      onPressed: () {
        HapticFeedback.lightImpact();
        action();
      },
    );

    return GestureDetector(
      key: const Key('queue_mini_player'),
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0),
        child: Row(
          children: [
            TrackArtwork(
              songId: song.id,
              title: song.title,
              size: 44,
              radius: 4,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    song.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontFamily: NothingTypography.fontUi,
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                      color: fg,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    song.artist,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontFamily: NothingTypography.fontUi,
                      fontSize: 12,
                      color: fg.withValues(alpha: 0.6),
                    ),
                  ),
                ],
              ),
            ),
            control(Icons.skip_previous, 24, onPrevious, 'Previous'),
            control(
              isPlaying ? Icons.pause : Icons.play_arrow,
              28,
              onPlayPause,
              isPlaying ? 'Pause' : 'Play',
            ),
            control(Icons.skip_next, 24, onNext, 'Next'),
          ],
        ),
      ),
    );
  }
}


/// Foreground for the queue panel: white on the dark tint, black on the
/// light one.
Color _fgOf(BuildContext context) =>
    Theme.of(context).brightness == Brightness.dark ? Colors.white : Colors.black;

Color _onFgOf(BuildContext context) =>
    Theme.of(context).brightness == Brightness.dark ? Colors.black : Colors.white;
