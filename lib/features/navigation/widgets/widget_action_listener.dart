import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../data/models/song_model.dart';
import '../../../shared/widgets/dot_toast.dart';
import '../../library/providers/library_provider.dart';
import '../../library/screens/smart_list_screen.dart';
import '../../player/providers/player_provider.dart';

/// Starts the mix chosen on the "Quick mixes" home-screen widget
/// (shuffle all / favorites / most played), once the library has loaded.
class WidgetActionListener extends StatefulWidget {
  final Widget child;
  const WidgetActionListener({super.key, required this.child});

  @override
  State<WidgetActionListener> createState() => _WidgetActionListenerState();
}

class _WidgetActionListenerState extends State<WidgetActionListener> {
  static const _channel = MethodChannel('lyro/actions');

  @override
  void initState() {
    super.initState();
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'action' && call.arguments is String) _run(call.arguments as String);
    });
    // Action that launched the app (cold start from the widget)
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      try {
        final pending = await _channel.invokeMethod<String>('takePendingAction');
        if (pending != null) _run(pending);
      } on MissingPluginException {
        // not on Android / in tests
      } on PlatformException {
        // ignore
      }
    });
  }

  @override
  void dispose() {
    _channel.setMethodCallHandler(null);
    super.dispose();
  }

  /// Waits (up to 15 s) for the library scan before playing.
  Future<bool> _libraryReady(LibraryProvider library) async {
    if (!library.isLoading && library.songs.isNotEmpty) return true;
    final done = Completer<bool>();
    void check() {
      if (!library.isLoading && !done.isCompleted) done.complete(library.songs.isNotEmpty);
    }

    library.addListener(check);
    check();
    final ok = await done.future.timeout(const Duration(seconds: 15), onTimeout: () => false);
    library.removeListener(check);
    return ok;
  }

  Future<void> _run(String action) async {
    final library = context.read<LibraryProvider>();
    final player = context.read<PlayerProvider>();
    if (!await _libraryReady(library) || !mounted) return;

    List<Song> songs;
    String source;
    switch (action) {
      case 'favorites':
        songs = [...library.favoriteSongs]..shuffle();
        source = 'Favorites';
      case 'most_played':
        songs = SmartList.mostPlayed.build(library.songs, library.playStats);
        source = 'Most played';
      default:
        songs = [...library.songs]..shuffle();
        source = 'Shuffle all';
    }
    if (songs.isEmpty) {
      // Nothing in that mix yet: fall back to shuffling everything
      showDotToast(context, action == 'favorites' ? 'No favorites yet · shuffling' : 'No plays yet · shuffling');
      songs = [...library.songs]..shuffle();
      source = 'Shuffle all';
    }
    await player.playSong(songs.first, songs, source: source);
    if (mounted) showDotToast(context, 'Playing · $source');
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
