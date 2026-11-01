import 'dart:async';
import 'package:audio_service/audio_service.dart';
import 'package:audio_session/audio_session.dart';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import '../models/song_model.dart';

/// Sleep timer state: either a wall-clock end, or "stop after this track".
class SleepTimer {
  final DateTime? endsAt;
  final bool endOfTrack;
  final Duration total;

  const SleepTimer({this.endsAt, this.endOfTrack = false, this.total = Duration.zero});

  Duration get remaining =>
      endsAt == null ? Duration.zero : endsAt!.difference(DateTime.now());
}

/// AudioHandler implementation using just_audio and audio_service.
/// Handles background playback, Android media notifications, iOS Now Playing,
/// lock-screen controls, bluetooth headset events, and audio interruptions.
///
/// The queue is a just_audio playlist, so the next track is prepared ahead of
/// time and albums play gaplessly. Optional fades soften track changes, and
/// a sleep timer can pause after a delay or at the end of the current track.
class LyroAudioHandler extends BaseAudioHandler with SeekHandler {
  final AudioPlayer _player = AudioPlayer();
  List<Song> _playlist = [];
  int _currentIndex = -1;

  /// Songs before this index came from the source the user played from
  /// (folder, album, ...); the rest is library fill-up.
  int _priorityEnd = 0;

  /// Fade length at track boundaries (0 = off).
  Duration fade = Duration.zero;
  double _duck = 1.0;

  /// Current sleep timer, or null when off.
  final ValueNotifier<SleepTimer?> sleepTimer = ValueNotifier(null);
  Timer? _sleepTicker;

  LyroAudioHandler() {
    _initAudio();
  }

  AudioPlayer get player => _player;
  List<Song> get playlist => List.unmodifiable(_playlist);
  int get currentIndex => _currentIndex;
  int get priorityEnd => _priorityEnd;
  Song? get currentSong => (_currentIndex >= 0 && _currentIndex < _playlist.length)
      ? _playlist[_currentIndex]
      : null;

  Future<void> _initAudio() async {
    // 1. Configure audio session for music playback & interruptions
    try {
      final session = await AudioSession.instance;
      await session.configure(const AudioSessionConfiguration.music());

      session.interruptionEventStream.listen((event) {
        if (event.begin) {
          switch (event.type) {
            case AudioInterruptionType.duck:
              _duck = 0.5;
              _applyVolume();
              break;
            case AudioInterruptionType.pause:
            case AudioInterruptionType.unknown:
              pause();
              break;
          }
        } else {
          switch (event.type) {
            case AudioInterruptionType.duck:
              _duck = 1.0;
              _applyVolume();
              break;
            case AudioInterruptionType.pause:
              play();
              break;
            case AudioInterruptionType.unknown:
              break;
          }
        }
      });

      // Handle headphone unplugged (Becoming noisy)
      session.becomingNoisyEventStream.listen((_) {
        pause();
      });
    } catch (e) {
      debugPrint('Audio session init error: $e');
    }

    // 2. Broadcast playback state changes to audio_service
    _player.playbackEventStream.listen(
      _broadcastPlaybackState,
      onError: (Object e, StackTrace st) {
        debugPrint('Playback error: $e');
      },
    );

    // 3. Track changes inside the playlist (gapless advance, seeks)
    _player.currentIndexStream.listen((index) {
      if (index == null || index == _currentIndex || index >= _playlist.length) return;
      final finished = _currentIndex;
      _currentIndex = index;
      mediaItem.add(_playlist[index].toMediaItem());
      _broadcastPlaybackState(_player.playbackEvent);
      if (finished >= 0 && sleepTimer.value?.endOfTrack == true) {
        cancelSleepTimer();
        pause();
      }
    });

    // 4. End of the whole queue
    _player.playerStateStream.listen((state) {
      if (state.processingState == ProcessingState.completed) {
        if (sleepTimer.value?.endOfTrack == true) cancelSleepTimer();
        _player.pause();
        _player.seek(Duration.zero, index: 0);
      }
    });

    // 5. Position updates (and fades)
    _player.positionStream.listen((pos) {
      _applyVolume(position: pos);
      _broadcastPlaybackState(_player.playbackEvent);
    });
  }

  void _applyVolume({Duration? position}) {
    var fadeFactor = 1.0;
    final f = fade.inMilliseconds;
    final dur = _player.duration;
    final pos = position ?? _player.position;
    if (f > 0 && dur != null && dur.inMilliseconds > f * 3) {
      final intoTrack = pos.inMilliseconds / f;
      final toEnd = (dur - pos).inMilliseconds / f;
      fadeFactor = [intoTrack, toEnd, 1.0].reduce((a, b) => a < b ? a : b).clamp(0.05, 1.0);
    }
    final target = _duck * fadeFactor;
    if ((_player.volume - target).abs() > 0.01) _player.setVolume(target);
  }

  void _broadcastPlaybackState(PlaybackEvent event) {
    final playing = _player.playing;
    final processingState = _player.processingState;

    playbackState.add(
      playbackState.value.copyWith(
        controls: [
          MediaControl.skipToPrevious,
          if (playing) MediaControl.pause else MediaControl.play,
          MediaControl.skipToNext,
          MediaControl.stop,
        ],
        systemActions: const {
          MediaAction.seek,
          MediaAction.seekForward,
          MediaAction.seekBackward,
          MediaAction.setShuffleMode,
          MediaAction.setRepeatMode,
        },
        androidCompactActionIndices: const [0, 1, 2],
        processingState: const {
          ProcessingState.idle: AudioProcessingState.idle,
          ProcessingState.loading: AudioProcessingState.loading,
          ProcessingState.buffering: AudioProcessingState.buffering,
          ProcessingState.ready: AudioProcessingState.ready,
          ProcessingState.completed: AudioProcessingState.completed,
        }[processingState]!,
        playing: playing,
        updatePosition: _player.position,
        bufferedPosition: _player.bufferedPosition,
        speed: _player.speed,
        queueIndex: _currentIndex >= 0 ? _currentIndex : null,
      ),
    );
  }

  static AudioSource? _source(Song song) {
    final Uri uri;
    if (song.path.isNotEmpty) {
      uri = Uri.file(song.path);
    } else if (song.uri != null) {
      uri = Uri.parse(song.uri!);
    } else {
      debugPrint('Invalid track URI/path for ${song.title}');
      return null;
    }
    return AudioSource.uri(uri, tag: song.toMediaItem());
  }

  void _publishQueue() {
    queue.add(_playlist.map((s) => s.toMediaItem()).toList());
    _broadcastPlaybackState(_player.playbackEvent);
  }

  /// Sets up a new playlist and starts playing from [initialIndex].
  /// The first [priorityCount] songs are the source the user played from.
  Future<void> loadQueue(
    List<Song> songs, {
    int initialIndex = 0,
    bool autoPlay = true,
    int? priorityCount,
  }) async {
    final playable = songs.where((s) => _source(s) != null).toList();
    if (playable.isEmpty) return;
    final start = songs[initialIndex.clamp(0, songs.length - 1)];

    _playlist = playable;
    _priorityEnd = (priorityCount ?? songs.length).clamp(0, playable.length);
    _currentIndex = playable.indexOf(start).clamp(0, playable.length - 1);
    mediaItem.add(_playlist[_currentIndex].toMediaItem());
    _publishQueue();

    try {
      await _player.setAudioSources(
        [for (final s in _playlist) _source(s)!],
        initialIndex: _currentIndex,
        initialPosition: Duration.zero,
      );
      if (autoPlay) await _player.play();
    } catch (e) {
      debugPrint('Error loading queue: $e');
      _broadcastPlaybackState(_player.playbackEvent);
    }
  }

  @override
  Future<void> play() => _player.play();

  @override
  Future<void> pause() => _player.pause();

  @override
  Future<void> stop() async {
    cancelSleepTimer();
    await _player.stop();
    await super.stop();
  }

  @override
  Future<void> seek(Duration position) => _player.seek(position);

  @override
  Future<void> skipToNext() async {
    if (_playlist.isEmpty) return;
    if (_player.hasNext) {
      await _player.seekToNext();
    } else if (playbackState.value.repeatMode == AudioServiceRepeatMode.all) {
      await _player.seek(Duration.zero, index: 0);
    } else {
      await _player.seek(Duration.zero);
      await _player.pause();
    }
  }

  @override
  Future<void> skipToPrevious() async {
    if (_playlist.isEmpty) return;

    // If more than 3 seconds in, restart track
    if (_player.position.inSeconds > 3 || !_player.hasPrevious) {
      await _player.seek(Duration.zero);
      return;
    }
    await _player.seekToPrevious();
  }

  @override
  Future<void> skipToQueueItem(int index) async {
    if (index >= 0 && index < _playlist.length) {
      await _player.seek(Duration.zero, index: index);
      if (!_player.playing) await _player.play();
    }
  }

  @override
  Future<void> setShuffleMode(AudioServiceShuffleMode shuffleMode) async {
    final isShuffled = shuffleMode != AudioServiceShuffleMode.none;
    playbackState.add(playbackState.value.copyWith(shuffleMode: shuffleMode));

    if (isShuffled && _playlist.isNotEmpty && _currentIndex >= 0) {
      // Shuffle within the source first, then within the library fill-up.
      final current = _playlist[_currentIndex];
      final source = <Song>[];
      final rest = <Song>[];
      for (var i = 0; i < _playlist.length; i++) {
        if (i == _currentIndex) continue;
        (i < _priorityEnd ? source : rest).add(_playlist[i]);
      }
      source.shuffle();
      rest.shuffle();
      final newPriorityEnd = _currentIndex < _priorityEnd ? source.length + 1 : source.length;
      await _replaceUpcoming(current, [...source, ...rest]);
      _priorityEnd = newPriorityEnd;
      _publishQueue();
    }
  }

  /// Keeps [current] playing and replaces everything else in the playlist
  /// without interrupting playback.
  Future<void> _replaceUpcoming(Song current, List<Song> others) async {
    final at = _currentIndex;
    final len = _playlist.length;
    if (at + 1 < len) await _player.removeAudioSourceRange(at + 1, len);
    if (at > 0) await _player.removeAudioSourceRange(0, at);
    _playlist = [current, ...others];
    _currentIndex = 0;
    await _player.addAudioSources([for (final s in others) _source(s)!]);
  }

  @override
  Future<void> setRepeatMode(AudioServiceRepeatMode repeatMode) async {
    playbackState.add(playbackState.value.copyWith(repeatMode: repeatMode));
    if (repeatMode == AudioServiceRepeatMode.one) {
      await _player.setLoopMode(LoopMode.one);
    } else if (repeatMode == AudioServiceRepeatMode.all) {
      await _player.setLoopMode(LoopMode.all);
    } else {
      await _player.setLoopMode(LoopMode.off);
    }
  }

  /// Reorders items in the queue
  void reorderQueue(int oldIndex, int newIndex) {
    if (oldIndex < 0 || oldIndex >= _playlist.length || newIndex < 0 || newIndex >= _playlist.length) {
      return;
    }
    final item = _playlist.removeAt(oldIndex);
    _playlist.insert(newIndex, item);
    _player.moveAudioSource(oldIndex, newIndex);
    if (oldIndex >= _priorityEnd && newIndex < _priorityEnd) {
      _priorityEnd++;
    } else if (oldIndex < _priorityEnd && newIndex >= _priorityEnd - 1) {
      _priorityEnd--;
    }

    if (_currentIndex == oldIndex) {
      _currentIndex = newIndex;
    } else if (oldIndex < _currentIndex && newIndex >= _currentIndex) {
      _currentIndex--;
    } else if (oldIndex > _currentIndex && newIndex <= _currentIndex) {
      _currentIndex++;
    }
    _publishQueue();
  }

  /// Inserts [songs] at [index] (e.g. "play next", or undoing a removal).
  Future<void> insertSongs(int index, List<Song> songs, {bool intoSource = true}) async {
    final valid = songs.where((s) => _source(s) != null).toList();
    if (valid.isEmpty) return;
    if (_playlist.isEmpty) {
      await loadQueue(valid, autoPlay: false);
      return;
    }
    final at = index.clamp(0, _playlist.length);
    _playlist.insertAll(at, valid);
    if (at <= _currentIndex) _currentIndex += valid.length;
    if (intoSource && at <= _priorityEnd) _priorityEnd += valid.length;
    await _player.insertAudioSources(at, [for (final s in valid) _source(s)!]);
    _publishQueue();
  }

  /// Queues [song] right after the current track; returns its position.
  Future<int> playNext(Song song) async {
    final at = _currentIndex + 1;
    await insertSongs(at, [song]);
    return at;
  }

  /// Queues [song] after the source's songs (before the library fill-up).
  Future<int> addToQueue(Song song) async {
    final at = _priorityEnd.clamp(_currentIndex + 1, _playlist.length);
    await insertSongs(at, [song]);
    return at;
  }

  @override
  Future<void> removeQueueItem(MediaItem mediaItem) async {
    final index = _playlist.indexWhere((s) => s.path == mediaItem.id);
    if (index >= 0) {
      await removeQueueItemAt(index);
    }
  }

  @override
  Future<void> removeQueueItemAt(int index) async {
    if (index < 0 || index >= _playlist.length) return;

    final wasCurrent = index == _currentIndex;
    _playlist.removeAt(index);
    if (index < _priorityEnd) _priorityEnd--;
    if (_playlist.isEmpty) {
      _currentIndex = -1;
      await stop();
    } else if (index < _currentIndex) {
      _currentIndex--;
    } else if (wasCurrent) {
      _currentIndex = _currentIndex.clamp(0, _playlist.length - 1);
    }
    await _player.removeAudioSourceAt(index);
    if (wasCurrent && _playlist.isNotEmpty) {
      mediaItem.add(_playlist[_currentIndex].toMediaItem());
    }
    _publishQueue();
  }

  /// Clears the upcoming queue, keeping the current song and optionally
  /// refilling it with [refill] so playback never runs dry.
  Future<void> clearQueue({List<Song> refill = const []}) async {
    final current = currentSong;
    if (current == null) {
      _playlist.clear();
      _currentIndex = -1;
      await stop();
      _publishQueue();
      return;
    }
    await _replaceUpcoming(current, [for (final s in refill) if (s.path != current.path) s]);
    _priorityEnd = 1;
    _publishQueue();
  }

  // ---------------------------------------------------------------- sleep

  /// Pauses after [duration], or at the end of the current track when
  /// [endOfTrack] is set.
  void setSleepTimer({Duration? duration, bool endOfTrack = false}) {
    _sleepTicker?.cancel();
    if (endOfTrack) {
      sleepTimer.value = const SleepTimer(endOfTrack: true);
      return;
    }
    if (duration == null) {
      cancelSleepTimer();
      return;
    }
    sleepTimer.value = SleepTimer(endsAt: DateTime.now().add(duration), total: duration);
    _sleepTicker = Timer.periodic(const Duration(seconds: 1), (t) {
      final timer = sleepTimer.value;
      if (timer == null || timer.remaining <= Duration.zero) {
        t.cancel();
        sleepTimer.value = null;
        if (timer != null) pause();
      } else {
        sleepTimer.value = SleepTimer(endsAt: timer.endsAt, total: timer.total);
      }
    });
  }

  void cancelSleepTimer() {
    _sleepTicker?.cancel();
    _sleepTicker = null;
    sleepTimer.value = null;
  }
}
