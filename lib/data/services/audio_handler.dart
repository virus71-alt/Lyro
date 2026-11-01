import 'dart:async';
import 'package:audio_service/audio_service.dart';
import 'package:audio_session/audio_session.dart';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import '../models/song_model.dart';

/// AudioHandler implementation using just_audio and audio_service.
/// Handles background playback, Android media notifications, iOS Now Playing,
/// lock-screen controls, bluetooth headset events, and audio interruptions.
class LyroAudioHandler extends BaseAudioHandler with SeekHandler {
  final AudioPlayer _player = AudioPlayer();
  List<Song> _playlist = [];
  int _currentIndex = -1;

  LyroAudioHandler() {
    _initAudio();
  }

  AudioPlayer get player => _player;
  List<Song> get playlist => List.unmodifiable(_playlist);
  int get currentIndex => _currentIndex;
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
              _player.setVolume(0.5);
              break;
            case AudioInterruptionType.pause:
            case AudioInterruptionType.unknown:
              pause();
              break;
          }
        } else {
          switch (event.type) {
            case AudioInterruptionType.duck:
              _player.setVolume(1.0);
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

    // 3. Handle track completion
    _player.playerStateStream.listen((state) {
      if (state.processingState == ProcessingState.completed) {
        skipToNext();
      }
    });

    // 4. Update position stream
    _player.positionStream.listen((pos) {
      _broadcastPlaybackState(_player.playbackEvent);
    });
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

  /// Sets up a new playlist and starts playing from [initialIndex]
  Future<void> loadQueue(List<Song> songs, {int initialIndex = 0, bool autoPlay = true}) async {
    if (songs.isEmpty) return;

    _playlist = List<Song>.from(songs);
    _currentIndex = initialIndex.clamp(0, _playlist.length - 1);

    // Update audio_service queue
    queue.add(_playlist.map((s) => s.toMediaItem()).toList());

    await _playTrackAtIndex(_currentIndex, autoPlay: autoPlay);
  }

  Future<void> _playTrackAtIndex(int index, {bool autoPlay = true}) async {
    if (index < 0 || index >= _playlist.length) return;

    _currentIndex = index;
    final song = _playlist[index];

    // Update MediaItem for notifications & lockscreen
    mediaItem.add(song.toMediaItem());

    try {
      // AudioSource from local file uri
      Uri uri;
      if (song.path.isNotEmpty) {
        uri = Uri.file(song.path);
      } else if (song.uri != null) {
        uri = Uri.parse(song.uri!);
      } else {
        debugPrint('Invalid track URI/path for ${song.title}');
        return;
      }

      await _player.setAudioSource(
        AudioSource.uri(uri, tag: song.toMediaItem()),
      );

      if (autoPlay) {
        await _player.play();
      }
    } catch (e) {
      debugPrint('Error playing audio file ${song.path}: $e');
      // If error occurs, attempt next track or update state
      _broadcastPlaybackState(_player.playbackEvent);
    }
  }

  @override
  Future<void> play() => _player.play();

  @override
  Future<void> pause() => _player.pause();

  @override
  Future<void> stop() async {
    await _player.stop();
    await super.stop();
  }

  @override
  Future<void> seek(Duration position) => _player.seek(position);

  @override
  Future<void> skipToNext() async {
    if (_playlist.isEmpty) return;

    if (_currentIndex < _playlist.length - 1) {
      await _playTrackAtIndex(_currentIndex + 1);
    } else {
      // End of playlist: check repeat mode
      if (playbackState.value.repeatMode == AudioServiceRepeatMode.all) {
        await _playTrackAtIndex(0);
      } else {
        await _player.seek(Duration.zero);
        await _player.pause();
      }
    }
  }

  @override
  Future<void> skipToPrevious() async {
    if (_playlist.isEmpty) return;

    // If more than 3 seconds in, restart track
    if (_player.position.inSeconds > 3) {
      await _player.seek(Duration.zero);
      return;
    }

    if (_currentIndex > 0) {
      await _playTrackAtIndex(_currentIndex - 1);
    } else {
      await _player.seek(Duration.zero);
    }
  }

  @override
  Future<void> skipToQueueItem(int index) async {
    if (index >= 0 && index < _playlist.length) {
      await _playTrackAtIndex(index);
    }
  }

  @override
  Future<void> setShuffleMode(AudioServiceShuffleMode shuffleMode) async {
    final isShuffled = shuffleMode != AudioServiceShuffleMode.none;
    playbackState.add(playbackState.value.copyWith(shuffleMode: shuffleMode));

    if (isShuffled && _playlist.isNotEmpty && _currentIndex >= 0) {
      final current = _playlist[_currentIndex];
      final others = List<Song>.from(_playlist)..removeAt(_currentIndex);
      others.shuffle();
      _playlist = [current, ...others];
      _currentIndex = 0;
      queue.add(_playlist.map((s) => s.toMediaItem()).toList());
    }
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

    if (_currentIndex == oldIndex) {
      _currentIndex = newIndex;
    } else if (oldIndex < _currentIndex && newIndex >= _currentIndex) {
      _currentIndex--;
    } else if (oldIndex > _currentIndex && newIndex <= _currentIndex) {
      _currentIndex++;
    }

    queue.add(_playlist.map((s) => s.toMediaItem()).toList());
    _broadcastPlaybackState(_player.playbackEvent);
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

    _playlist.removeAt(index);
    if (_currentIndex == index) {
      if (_playlist.isNotEmpty) {
        await _playTrackAtIndex(_currentIndex.clamp(0, _playlist.length - 1));
      } else {
        await stop();
        _currentIndex = -1;
      }
    } else if (index < _currentIndex) {
      _currentIndex--;
    }

    queue.add(_playlist.map((s) => s.toMediaItem()).toList());
    _broadcastPlaybackState(_player.playbackEvent);
  }

  /// Clears the upcoming queue
  void clearQueue() {
    if (_currentIndex >= 0 && _currentIndex < _playlist.length) {
      _playlist = [_playlist[_currentIndex]];
      _currentIndex = 0;
    } else {
      _playlist.clear();
      _currentIndex = -1;
      stop();
    }
    queue.add(_playlist.map((s) => s.toMediaItem()).toList());
    _broadcastPlaybackState(_player.playbackEvent);
  }
}
