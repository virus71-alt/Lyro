class Formatters {
  Formatters._();

  /// Formats duration in milliseconds or Duration into "mm:ss" or "hh:mm:ss"
  static String formatDuration(Duration? duration) {
    if (duration == null || duration.inMilliseconds <= 0) {
      return '00:00';
    }

    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60);

    final minStr = minutes.toString().padLeft(2, '0');
    final secStr = seconds.toString().padLeft(2, '0');

    if (hours > 0) {
      return '$hours:$minStr:$secStr';
    }
    return '$minStr:$secStr';
  }

  /// Formats millisecond integer duration
  static String formatMs(int? milliseconds) {
    if (milliseconds == null || milliseconds <= 0) return '00:00';
    return formatDuration(Duration(milliseconds: milliseconds));
  }

  /// Total play time as "12H 04M" or "45M"
  static String formatPlayTime(int? milliseconds) {
    final total = Duration(milliseconds: milliseconds ?? 0).inMinutes;
    if (total < 60) return '${total}M';
    return '${total ~/ 60}H ${(total % 60).toString().padLeft(2, '0')}M';
  }

  /// Normalizes and cleans file format / codec representation
  static String formatCodec(String? extension, String? path) {
    if (extension != null && extension.isNotEmpty) {
      return extension.toUpperCase().replaceAll('.', '');
    }
    if (path != null && path.contains('.')) {
      return path.split('.').last.toUpperCase();
    }
    return 'AUDIO';
  }

  /// Formats track count string
  static String formatTrackCount(int count) {
    if (count == 1) return '1 TRACK';
    return '$count TRACKS';
  }

  /// Formats byte sizes into human-readable string (KB, MB, GB)
  static String formatFileSize(int bytes) {
    if (bytes <= 0) return '0 B';
    const suffixes = ['B', 'KB', 'MB', 'GB'];
    var i = 0;
    double size = bytes.toDouble();
    while (size >= 1024 && i < suffixes.length - 1) {
      size /= 1024;
      i++;
    }
    return '${size.toStringAsFixed(1)} ${suffixes[i]}';
  }
}

