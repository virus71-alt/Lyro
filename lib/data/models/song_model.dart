import 'package:audio_service/audio_service.dart';
import '../../core/utils/formatters.dart';

class Song {
  final int id;
  final String title;
  final String artist;
  final String album;
  final int? albumId;
  final int duration; // in milliseconds
  final String path;
  final String? uri;
  final String format;
  final int? trackNumber;
  final int? size;
  final int? dateAdded;

  const Song({
    required this.id,
    required this.title,
    required this.artist,
    required this.album,
    this.albumId,
    required this.duration,
    required this.path,
    this.uri,
    required this.format,
    this.trackNumber,
    this.size,
    this.dateAdded,
  });

  bool get isFlac => format.toLowerCase() == 'flac';

  String get formattedDuration => Formatters.formatMs(duration);

  MediaItem toMediaItem() {
    return MediaItem(
      id: path.isNotEmpty ? path : id.toString(),
      album: album,
      title: title,
      artist: artist,
      duration: Duration(milliseconds: duration),
      artUri: null, // Artwork loaded via OnAudioQuery ArtworkWidget or custom
      extras: {
        'songId': id,
        'albumId': albumId,
        'path': path,
        'format': format,
        'isFlac': isFlac,
      },
    );
  }

  factory Song.fromMap(Map<String, dynamic> map) {
    return Song(
      id: map['id'] as int? ?? 0,
      title: (map['title'] as String?)?.trim().isNotEmpty == true
          ? (map['title'] as String).trim()
          : 'Unknown Title',
      artist: (map['artist'] as String?)?.trim().isNotEmpty == true
          ? (map['artist'] as String).trim()
          : 'Unknown Artist',
      album: (map['album'] as String?)?.trim().isNotEmpty == true
          ? (map['album'] as String).trim()
          : 'Unknown Album',
      albumId: map['albumId'] as int?,
      duration: map['duration'] as int? ?? 0,
      path: map['path'] as String? ?? '',
      uri: map['uri'] as String?,
      format: Formatters.formatCodec(map['format'] as String?, map['path'] as String?),
      trackNumber: map['trackNumber'] as int?,
      size: map['size'] as int?,
      dateAdded: map['dateAdded'] as int?,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'title': title,
      'artist': artist,
      'album': album,
      'albumId': albumId,
      'duration': duration,
      'path': path,
      'uri': uri,
      'format': format,
      'trackNumber': trackNumber,
      'size': size,
      'dateAdded': dateAdded,
    };
  }

  Song copyWith({
    int? id,
    String? title,
    String? artist,
    String? album,
    int? albumId,
    int? duration,
    String? path,
    String? uri,
    String? format,
    int? trackNumber,
    int? size,
    int? dateAdded,
  }) {
    return Song(
      id: id ?? this.id,
      title: title ?? this.title,
      artist: artist ?? this.artist,
      album: album ?? this.album,
      albumId: albumId ?? this.albumId,
      duration: duration ?? this.duration,
      path: path ?? this.path,
      uri: uri ?? this.uri,
      format: format ?? this.format,
      trackNumber: trackNumber ?? this.trackNumber,
      size: size ?? this.size,
      dateAdded: dateAdded ?? this.dateAdded,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Song && runtimeType == other.runtimeType && id == other.id && path == other.path;

  @override
  int get hashCode => id.hashCode ^ path.hashCode;
}

