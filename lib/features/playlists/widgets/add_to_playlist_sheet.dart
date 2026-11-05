import 'package:flutter/material.dart';

import '../../../core/theme/nothing_palette.dart';

import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_typography.dart';
import '../../../data/models/song_model.dart';
import '../../../shared/widgets/dot_toast.dart';
import '../providers/playlist_provider.dart';

/// Asks for a playlist name; returns null when cancelled.
Future<String?> askPlaylistName(
  BuildContext context, {
  String title = 'New playlist',
  String initial = '',
  String hint = 'Playlist name',
}) {
  return showDialog<String>(
    context: context,
    builder: (_) => _NameDialog(title: title, initial: initial, hint: hint),
  );
}

/// Owns its text controller, which has to outlive the closing animation.
class _NameDialog extends StatefulWidget {
  final String title, initial, hint;
  const _NameDialog({required this.title, required this.initial, required this.hint});

  @override
  State<_NameDialog> createState() => _NameDialogState();
}

class _NameDialogState extends State<_NameDialog> {
  late final TextEditingController _controller = TextEditingController(text: widget.initial);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = NothingPalette.of(context);
    return AlertDialog(
      backgroundColor: c.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
        side: BorderSide(color: c.line),
      ),
      title: Text(
        widget.title.toUpperCase(),
        style: NothingTypography.monoLabel(isDark: c.dark),
      ),
      content: TextField(
        key: const Key('playlist_name_field'),
        controller: _controller,
        autofocus: true,
        maxLength: 40,
        textCapitalization: TextCapitalization.words,
        cursorColor: NothingColors.signalRed,
        style: TextStyle(
          fontFamily: NothingTypography.fontUi,
          fontSize: 16,
          color: c.display,
        ),
        decoration: InputDecoration(
          hintText: widget.hint,
          focusedBorder: UnderlineInputBorder(
            borderSide: BorderSide(color: NothingColors.signalRed),
          ),
        ),
        onSubmitted: (v) => Navigator.pop(context, v),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(
            'CANCEL',
            style: NothingTypography.monoLabel(isDark: c.dark),
          ),
        ),
        TextButton(
          key: const Key('playlist_name_ok'),
          onPressed: () => Navigator.pop(context, _controller.text),
          child: Text(
            'SAVE',
            style: NothingTypography.monoLabel(
              isDark: c.dark,
              color: NothingColors.signalRedBright,
            ),
          ),
        ),
      ],
    );
  }
}

/// Pick a playlist (or create one) to add [songs] to.
class AddToPlaylistSheet extends StatelessWidget {
  final List<Song> songs;
  const AddToPlaylistSheet({super.key, required this.songs});

  static Future<void> show(BuildContext context, List<Song> songs) {
    return showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => AddToPlaylistSheet(songs: songs),
    ).then((_) {});
  }

  @override
  Widget build(BuildContext context) {
    final c = NothingPalette.of(context);
    final provider = context.watch<PlaylistProvider>();
    final playlists = provider.playlists;
    final messengerContext = Navigator.of(context).context;

    Future<void> addTo(String id, String name) async {
      HapticFeedback.lightImpact();
      final added = await provider.addSongs(id, songs);
      if (context.mounted) Navigator.pop(context);
      if (messengerContext.mounted) {
        showDotToast(
          messengerContext,
          added == 0 ? 'Already in $name' : 'Added to $name',
        );
      }
    }

    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.sizeOf(context).height * 0.7,
      ),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
        border: Border(top: BorderSide(color: c.line)),
      ),
      // Material layer so the list rows' tap ripples are visible
      child: Material(
        type: MaterialType.transparency,
        child: SafeArea(
          top: false,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 18, 20, 8),
                child: Text(
                  songs.length == 1
                      ? 'ADD TO PLAYLIST'
                      : 'ADD ${songs.length} SONGS TO PLAYLIST',
                  style: NothingTypography.monoLabel(isDark: c.dark),
                ),
              ),
              ListTile(
                key: const Key('new_playlist_tile'),
                leading: Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: NothingColors.signalRed,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(Icons.add, color: Colors.white),
                ),
                title: Text(
                  'New playlist',
                  style: TextStyle(
                    fontFamily: NothingTypography.fontUi,
                    fontWeight: FontWeight.w600,
                    color: c.display,
                  ),
                ),
                onTap: () async {
                  final name = await askPlaylistName(context);
                  if (name == null) return;
                  final p = await provider.create(name);
                  await addTo(p.id, p.name);
                },
              ),
              Flexible(
                child: ListView.builder(
                  shrinkWrap: true,
                  itemCount: playlists.length,
                  itemBuilder: (context, i) {
                    final p = playlists[i];
                    return ListTile(
                      leading: Container(
                        width: 44,
                        height: 44,
                        decoration: BoxDecoration(
                          color: c.raised,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: c.line),
                        ),
                        child: Icon(Icons.queue_music, color: c.secondary),
                      ),
                      title: Text(
                        p.name,
                        style: TextStyle(
                          fontFamily: NothingTypography.fontUi,
                          color: c.display,
                        ),
                      ),
                      subtitle: Text(
                        '${p.songIds.length} TRACKS',
                        style: NothingTypography.monoLabel(
                          isDark: c.dark,
                          color: c.muted,
                        ),
                      ),
                      onTap: () => addTo(p.id, p.name),
                    );
                  },
                ),
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }
}
