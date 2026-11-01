import 'package:flutter/material.dart';
import 'package:on_audio_query/on_audio_query.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../core/theme/nothing_theme.dart';
import '../../../core/theme/nothing_typography.dart';

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
      child: QueryArtworkWidget(
        key: ValueKey('artwork_${type.name}_$songId'),
        id: songId,
        type: type,
        artworkBorder: BorderRadius.circular(radius),
        artworkWidth: size,
        artworkHeight: size,
        artworkFit: BoxFit.cover,
        keepOldArtwork: true,
        nullArtworkWidget: _buildFallback(isDark),
        errorBuilder: (context, error, stackTrace) => _buildFallback(isDark),
      ),
    );
  }

  Widget _buildDecoratedContainer({required bool isDark, required Widget child}) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: Container(
        width: size,
        height: size,
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
    final isLarge = size > 100;

    return Container(
      width: size,
      height: size,
      color: isDark ? NothingColors.darkRaised : NothingColors.lightRaised,
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Subtle concentric rings / vinyl groove effect
          if (isLarge)
            CustomPaint(
              size: Size(size, size),
              painter: _VinylGroovePainter(isDark: isDark),
            ),
          Center(
            child: Text(
              initial,
              style: NothingTypography.dottedBold(
                isDark: isDark,
                fontSize: size * 0.40,
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
