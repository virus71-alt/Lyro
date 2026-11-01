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
}

