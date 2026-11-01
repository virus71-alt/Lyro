import 'dart:io';
import 'dart:typed_data';

/// Finds a cover image stored next to a song.
///
/// Only images that are clearly the song's or the album's are used: one
/// named after the song file, or a conventional album-art name such as
/// `folder.jpg` / `cover.png`. Arbitrary photos in the same folder (e.g. a
/// camera image in Downloads) are never picked. Needs the photos permission
/// on Android 13+; without it the lookup simply finds nothing.
class FolderArtwork {
  FolderArtwork._();

  static const _extensions = {'jpg', 'jpeg', 'png', 'webp'};
  static const _albumNames = [
    'cover', 'folder', 'front', 'albumart', 'album', 'albumartsmall', 'thumb',
  ];
  static const int _maxBytes = 12 * 1024 * 1024;

  /// Directory → its image files (lower-case name → path), listed once.
  static final Map<String, Map<String, String>> _dirImages = {};

  static Future<Uint8List?> read(String songPath) async {
    if (songPath.isEmpty) return null;
    try {
      final file = File(songPath);
      final dir = file.parent.path;
      final images = _dirImages[dir] ??= await _listImages(dir);
      if (images.isEmpty) return null;

      final songName = file.uri.pathSegments.last.toLowerCase();
      final songBase = songName.contains('.')
          ? songName.substring(0, songName.lastIndexOf('.'))
          : songName;

      String? pick;
      // 1. An image named after this song ("01 - Song.jpg", "01 - Song.flac.png")
      for (final entry in images.entries) {
        final base = _base(entry.key);
        if (base == songBase || base == songName) {
          pick = entry.value;
          break;
        }
      }
      // 2. Conventional album-art names, in priority order
      if (pick == null) {
        for (final name in _albumNames) {
          final hit = images.entries
              .where((e) => _base(e.key) == name || _base(e.key).startsWith('$name '))
              .firstOrNull;
          if (hit != null) {
            pick = hit.value;
            break;
          }
        }
      }
      if (pick == null) return null;

      final image = File(pick);
      if (await image.length() > _maxBytes) return null;
      return await image.readAsBytes();
    } catch (_) {
      return null; // no permission or unreadable
    }
  }

  static String _base(String name) =>
      name.contains('.') ? name.substring(0, name.lastIndexOf('.')) : name;

  static Future<Map<String, String>> _listImages(String dir) async {
    final out = <String, String>{};
    try {
      await for (final entity in Directory(dir).list(followLinks: false)) {
        if (entity is! File) continue;
        final name = entity.uri.pathSegments.last.toLowerCase();
        final dot = name.lastIndexOf('.');
        if (dot > 0 && _extensions.contains(name.substring(dot + 1))) {
          out[name] = entity.path;
        }
      }
    } catch (_) {
      // unreadable directory
    }
    return out;
  }

  /// Forget directory listings (e.g. after a rescan or a permission grant).
  static void clear() => _dirImages.clear();
}
