class Album {
  final int id;
  final String album;
  final String artist;
  final int numOfSongs;
  final int? firstYear;
  final int? lastYear;
  final int? songId;

  const Album({
    required this.id,
    required this.album,
    required this.artist,
    required this.numOfSongs,
    this.firstYear,
    this.lastYear,
    this.songId,
  });

  Album copyWith({
    int? id,
    String? album,
    String? artist,
    int? numOfSongs,
    int? firstYear,
    int? lastYear,
    int? songId,
  }) {
    return Album(
      id: id ?? this.id,
      album: album ?? this.album,
      artist: artist ?? this.artist,
      numOfSongs: numOfSongs ?? this.numOfSongs,
      firstYear: firstYear ?? this.firstYear,
      lastYear: lastYear ?? this.lastYear,
      songId: songId ?? this.songId,
    );
  }

  factory Album.fromMap(Map<String, dynamic> map) {
    return Album(
      id: map['id'] as int? ?? 0,
      album: (map['album'] as String?)?.trim().isNotEmpty == true
          ? (map['album'] as String).trim()
          : 'Unknown Album',
      artist: (map['artist'] as String?)?.trim().isNotEmpty == true
          ? (map['artist'] as String).trim()
          : 'Unknown Artist',
      numOfSongs: map['numOfSongs'] as int? ?? 0,
      firstYear: map['firstYear'] as int?,
      lastYear: map['lastYear'] as int?,
      songId: map['songId'] as int?,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'album': album,
      'artist': artist,
      'numOfSongs': numOfSongs,
      'firstYear': firstYear,
      'lastYear': lastYear,
      if (songId != null) 'songId': songId,
    };
  }
}

