class Artist {
  final int id;
  final String artist;
  final int numberOfAlbums;
  final int numberOfTracks;

  const Artist({
    required this.id,
    required this.artist,
    required this.numberOfAlbums,
    required this.numberOfTracks,
  });

  factory Artist.fromMap(Map<String, dynamic> map) {
    return Artist(
      id: map['id'] as int? ?? 0,
      artist: (map['artist'] as String?)?.trim().isNotEmpty == true
          ? (map['artist'] as String).trim()
          : 'Unknown Artist',
      numberOfAlbums: map['numberOfAlbums'] as int? ?? 0,
      numberOfTracks: map['numberOfTracks'] as int? ?? 0,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'artist': artist,
      'numberOfAlbums': numberOfAlbums,
      'numberOfTracks': numberOfTracks,
    };
  }
}

