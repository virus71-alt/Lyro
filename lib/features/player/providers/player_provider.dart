import 'dart:async';
import 'package:audio_service/audio_service.dart';
import 'package:flutter/foundation.dart';
import '../../../data/models/song_model.dart';
import '../../../data/repositories/preferences_repository.dart';
import '../../../data/services/audio_handler.dart';

class PlayerProvider extends ChangeNotifier {
  final LyroAudioHandler audioHandler;
  final PreferencesRepository prefsRepo;

  StreamSubscription? _mediaItemSub;
  StreamSubscription? _playbackStateSub;
  StreamSubscription? _queueSub;

  Song? _currentSong;
  bool _isPlaying = false;
  bool _isBuffering = false;
  Duration _duration = Duration.zero;
  List<Song> _queue = [];
  int _queueIndex = 0;
  bool _isShuffle = false;
  AudioServiceRepeatMode _repeatMode = AudioServiceRepeatMode.none;
  Set<int> _favorites = {};
  String _queueSource = 'Library';
  String? _lastRecordedPath;

  /// Supplies the whole library, used to keep the queue topped up after the
  /// songs of the folder/album/artist being played.
  List<Song> Function()? librarySongs;

  // High-frequency position notifiers to prevent rebuilding the whole widget tree
  final ValueNotifier<Duration> positionNotifier = ValueNotifier<Duration>(Duration.zero);
  final ValueNotifier<Duration> bufferedPositionNotifier = ValueNotifier<Duration>(Duration.zero);

  PlayerProvider({
    required this.audioHandler,
    required this.prefsRepo,
  }) {
    _favorites = prefsRepo.getFavorites();
    _initSubscriptions();
  }

  // Getters
  Song? get currentSong => _currentSong;
  bool get isPlaying => _isPlaying;
  bool get isBuffering => _isBuffering;
  Duration get position => positionNotifier.value;
  Duration get bufferedPosition => bufferedPositionNotifier.value;
  ValueListenable<Duration> get positionListenable => positionNotifier;
  ValueListenable<Duration> get bufferedPositionListenable => bufferedPositionNotifier;
  Duration get duration => _duration;
  List<Song> get queue => _queue;
  int get queueIndex => _queueIndex;
  bool get isShuffle => _isShuffle;
  AudioServiceRepeatMode get repeatMode => _repeatMode;
  bool get hasTrack => _currentSong != null;

  /// Where playback was started from, e.g. "Folder · Downloads".
  String get queueSource => _queueSource;

  /// Number of leading queue entries that belong to [queueSource].
  int get priorityEnd => audioHandler.priorityEnd;
  bool isFavorite(int songId) => _favorites.contains(songId);

  void _initSubscriptions() {
    _mediaItemSub = audioHandler.mediaItem.listen((item) {
      if (item == null) {
        _currentSong = null;
        _duration = Duration.zero;
        positionNotifier.value = Duration.zero;
        bufferedPositionNotifier.value = Duration.zero;
      } else {
        _duration = item.duration ?? Duration.zero;
        // Match with queue or construct
        final found = _queue.where((s) => s.path == item.id).firstOrNull;
        if (found != null) {
          _currentSong = found;
        } else {
          _currentSong = Song(
            id: (item.extras?['songId'] as int?) ?? 0,
            title: item.title,
            artist: item.artist ?? 'Unknown Artist',
            album: item.album ?? 'Unknown Album',
            albumId: item.extras?['albumId'] as int?,
            duration: item.duration?.inMilliseconds ?? 0,
            path: item.id,
            format: (item.extras?['format'] as String?) ?? 'AUDIO',
          );
        }

        final song = _currentSong;
        if (song != null && song.path != _lastRecordedPath) {
          _lastRecordedPath = song.path;
          prefsRepo.addRecentlyPlayed(song.id);
          prefsRepo.recordPlay(song.id);
        }
      }
      notifyListeners();
    });

    _playbackStateSub = audioHandler.playbackState.listen((state) {
      // 1. Update high-frequency position notifiers (isolated updates)
      if (positionNotifier.value != state.position) {
        positionNotifier.value = state.position;
      }
      if (bufferedPositionNotifier.value != state.bufferedPosition) {
        bufferedPositionNotifier.value = state.bufferedPosition;
      }

      // 2. Only trigger full notifyListeners if structural playback state changed
      final newPlaying = state.playing;
      final newBuffering = state.processingState == AudioProcessingState.buffering;
      final newShuffle = state.shuffleMode != AudioServiceShuffleMode.none;
      final newRepeat = state.repeatMode;
      final newQueueIndex = state.queueIndex ?? 0;

      final bool hasStructuralChange = _isPlaying != newPlaying ||
          _isBuffering != newBuffering ||
          _isShuffle != newShuffle ||
          _repeatMode != newRepeat ||
          _queueIndex != newQueueIndex;

      if (hasStructuralChange) {
        _isPlaying = newPlaying;
        _isBuffering = newBuffering;
        _isShuffle = newShuffle;
        _repeatMode = newRepeat;
        _queueIndex = newQueueIndex;
        notifyListeners();
      }
    });

    _queueSub = audioHandler.queue.listen((mediaItems) {
      _queue = audioHandler.playlist;
      notifyListeners();
    });
  }

  /// Plays [song] from [playlist] (the folder, album, ... it was tapped in).
  ///
  /// The queue is the source songs in their order, followed by the rest of the
  /// library, so it never runs empty while the source keeps priority.
  Future<void> playSong(Song song, List<Song> playlist, {String? source}) async {
    final ordered = queueOrder(song, playlist, source: source);
    final inContext = {for (final s in ordered) s.path};
    final rest = (librarySongs?.call() ?? const <Song>[])
        .where((s) => !inContext.contains(s.path))
        .toList()
      ..shuffle();
    final full = [...ordered, ...rest];

    _queue = full;
    _queueIndex = 0;
    _queueSource = source ?? 'Library';
    _currentSong = song;
    positionNotifier.value = Duration.zero;
    notifyListeners();

    await audioHandler.loadQueue(
      full,
      initialIndex: 0,
      priorityCount: ordered.length,
    );
  }

  /// The chosen [song] always plays first. Albums and playlists then keep
  /// their running order (wrapping round to the songs before it); any other
  /// source (all songs, folders, search, favorites...) continues shuffled.
  @visibleForTesting
  static List<Song> queueOrder(Song song, List<Song> playlist, {String? source}) {
    final keepOrder =
        source != null && (source.startsWith('Album') || source.startsWith('Playlist'));
    final index = playlist.indexOf(song);
    if (keepOrder && index >= 0) {
      return [song, ...playlist.sublist(index + 1), ...playlist.sublist(0, index)];
    }
    final others = [for (final s in playlist) if (s != song) s]..shuffle();
    return [song, ...others];
  }

  /// Plays or pauses current playback
  Future<void> togglePlayPause() async {
    if (_isPlaying) {
      await audioHandler.pause();
    } else {
      await audioHandler.play();
    }
  }

  Future<void> playOrPause() => togglePlayPause();

  Future<void> seek(Duration position) async {
    positionNotifier.value = position;
    await audioHandler.seek(position);
  }

  Future<void> skipToNext() async {
    await audioHandler.skipToNext();
  }

  Future<void> skipToPrevious() async {
    await audioHandler.skipToPrevious();
  }

  Future<void> skipToQueueItem(int index) async {
    await audioHandler.skipToQueueItem(index);
  }

  Future<void> toggleShuffle() async {
    final newMode = _isShuffle
        ? AudioServiceShuffleMode.none
        : AudioServiceShuffleMode.all;
    await audioHandler.setShuffleMode(newMode);
  }

  Future<void> toggleRepeat() async {
    AudioServiceRepeatMode newMode;
    switch (_repeatMode) {
      case AudioServiceRepeatMode.none:
        newMode = AudioServiceRepeatMode.all;
        break;
      case AudioServiceRepeatMode.all:
        newMode = AudioServiceRepeatMode.one;
        break;
      case AudioServiceRepeatMode.one:
      case AudioServiceRepeatMode.group:
        newMode = AudioServiceRepeatMode.none;
        break;
    }
    await audioHandler.setRepeatMode(newMode);
  }

  Future<void> toggleFavorite(int songId) async {
    await prefsRepo.toggleFavorite(songId);
    _favorites = prefsRepo.getFavorites();
    notifyListeners();
  }

  void reorderQueue(int oldIndex, int newIndex) {
    audioHandler.reorderQueue(oldIndex, newIndex);
    _queue = audioHandler.playlist;
    notifyListeners();
  }

  void removeQueueItem(int index) {
    audioHandler.removeQueueItemAt(index);
    _queue = audioHandler.playlist;
    notifyListeners();
  }

  /// Queues [song] right after the current one; returns its queue position.
  Future<int> playNext(Song song) async {
    final at = await audioHandler.playNext(song);
    _queue = audioHandler.playlist;
    notifyListeners();
    return at - _queueIndex;
  }

  /// Queues [song] after the songs of the current source; returns how many
  /// songs come before it.
  Future<int> addToQueue(Song song) async {
    final at = await audioHandler.addToQueue(song);
    _queue = audioHandler.playlist;
    notifyListeners();
    return at - _queueIndex;
  }

  /// Puts [song] back at [index] (undo of a removal).
  Future<void> insertQueueItem(int index, Song song) async {
    await audioHandler.insertSongs(index, [song]);
    _queue = audioHandler.playlist;
    notifyListeners();
  }

  // Sleep timer
  ValueListenable<SleepTimer?> get sleepTimer => audioHandler.sleepTimer;

  void setSleepTimer({Duration? duration, bool endOfTrack = false}) =>
      audioHandler.setSleepTimer(duration: duration, endOfTrack: endOfTrack);

  void cancelSleepTimer() => audioHandler.cancelSleepTimer();

  void clearQueue() {
    final current = _currentSong?.path;
    audioHandler.clearQueue(
      refill: [
        for (final s in librarySongs?.call() ?? const <Song>[])
          if (s.path != current) s,
      ],
    );
    _queueSource = 'Library';
    _queue = audioHandler.playlist;
    notifyListeners();
  }

  @override
  void dispose() {
    _mediaItemSub?.cancel();
    _playbackStateSub?.cancel();
    _queueSub?.cancel();
    positionNotifier.dispose();
    bufferedPositionNotifier.dispose();
    super.dispose();
  }
}
