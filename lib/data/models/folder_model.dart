import 'song_model.dart';

class MusicFolder {
  final String path;
  final String name;
  final List<Song> songs;

  const MusicFolder({
    required this.path,
    required this.name,
    required this.songs,
  });

  int get songCount => songs.length;

  /// Total play time of the folder, in milliseconds.
  int get totalDuration => songs.fold(0, (ms, s) => ms + s.duration);
}

