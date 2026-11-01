import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:on_audio_query/on_audio_query.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';
import 'artwork_cache.dart';

class TrackArtwork extends StatelessWidget {
  final int songId;
  final String title;
  final double size;
  final double radius;
  final ArtworkType type;

  const TrackArtwork({
    super.key,
    required this.songId,
    required this.title,
    this.size = 48.0,
    this.radius = NothingColors.radiusSm,
    this.type = ArtworkType.AUDIO,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;

    if (songId <= 0) {
      return _buildDecoratedContainer(
        isDark: isDark,
        child: _buildFallback(isDark),
      );
    }

    return _buildDecoratedContainer(
      isDark: isDark,
      child: _CachedArtwork(
        key: ValueKey('artwork_${type.name}_$songId'),
        id: songId,
        type: type,
        size: size,
        fallback: _buildFallback(isDark),
      ),
    );
  }

  Widget _buildDecoratedContainer({required bool isDark, required Widget child}) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: Container(
        width: size.isFinite ? size : null,
        height: size.isFinite ? size : null,
        decoration: BoxDecoration(
          color: isDark ? NothingColors.darkRaised : NothingColors.lightRaised,
          borderRadius: BorderRadius.circular(radius),
          border: Border.all(
            color: isDark ? NothingColors.darkLine : NothingColors.lightLine,
            width: 1.0,
          ),
        ),
        child: child,
      ),
    );
  }

  Widget _buildFallback(bool isDark) {
    final initial = title.trim().isNotEmpty ? title.trim()[0].toUpperCase() : 'L';
    final safeSize = (size.isFinite && size > 0) ? size : 48.0;
    final isLarge = safeSize > 100;

    return Container(
      width: size.isFinite ? size : null,
      height: size.isFinite ? size : null,
      color: isDark ? NothingColors.darkRaised : NothingColors.lightRaised,
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Subtle concentric rings / vinyl groove effect
          if (isLarge)
            CustomPaint(
              size: Size(safeSize, safeSize),
              painter: _VinylGroovePainter(isDark: isDark),
            ),
          Center(
            child: Text(
              initial,
              style: NothingTypography.dottedBold(
                isDark: isDark,
                fontSize: safeSize * 0.40,
                color: isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _VinylGroovePainter extends CustomPainter {
  final bool isDark;

  _VinylGroovePainter({required this.isDark});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final paint = Paint()
      ..color = (isDark ? Colors.white : Colors.black).withValues(alpha: 0.04)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0;

    final maxRadius = size.width / 2;
    for (double r = maxRadius * 0.4; r < maxRadius * 0.9; r += 14.0) {
      canvas.drawCircle(center, r, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _VinylGroovePainter oldDelegate) =>
      oldDelegate.isDark != isDark;
}

/// Artwork resolved once through [ArtworkCache] and decoded at display size.
class _CachedArtwork extends StatefulWidget {
  final int id;
  final ArtworkType type;
  final double size;
  final Widget fallback;

  const _CachedArtwork({
    super.key,
    required this.id,
    required this.type,
    required this.size,
    required this.fallback,
  });

  @override
  State<_CachedArtwork> createState() => _CachedArtworkState();
}

class _CachedArtworkState extends State<_CachedArtwork> {
  Uint8List? _bytes;
  bool _resolved = false;
  int _bucket = 0;
  double _dpr = 1; // read only in didChangeDependencies

  @override
  void initState() {
    super.initState();
    ArtworkCache.revision.addListener(_retryIfBlank);
  }

  @override
  void dispose() {
    ArtworkCache.revision.removeListener(_retryIfBlank);
    super.dispose();
  }

  /// A cover source became available (permission granted, online enabled).
  void _retryIfBlank() {
    if (_bytes != null || !mounted) return;
    _bucket = 0;
    setState(_resolve); // a cache hit resolves synchronously
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _dpr = MediaQuery.devicePixelRatioOf(context);
    _resolve();
  }

  void _resolve() {
    final safeSize = (widget.size.isFinite && widget.size > 0) ? widget.size : 48.0;
    final bucket = ArtworkCache.bucket(safeSize * _dpr);
    if (bucket == _bucket) return;
    _bucket = bucket;
    final cached = ArtworkCache.peek(widget.id, widget.type, bucket);
    if (cached.hit) {
      _bytes = cached.bytes;
      _resolved = true;
      return;
    }
    ArtworkCache.load(widget.id, widget.type, bucket).then((bytes) {
      if (!mounted || bucket != _bucket) return;
      setState(() {
        _bytes = bytes;
        _resolved = true;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    final bytes = _bytes;
    if (bytes == null) {
      return _resolved ? widget.fallback : const SizedBox.expand();
    }
    final safeSize = (widget.size.isFinite && widget.size > 0) ? widget.size : 48.0;
    final px = (safeSize * MediaQuery.devicePixelRatioOf(context)).round();
    return Image.memory(
      bytes,
      width: widget.size.isFinite ? widget.size : null,
      height: widget.size.isFinite ? widget.size : null,
      fit: BoxFit.cover,
      cacheWidth: px > 0 ? px : null,
      gaplessPlayback: true,
      filterQuality: FilterQuality.medium,
      errorBuilder: (_, _, _) => widget.fallback,
    );
  }
}
