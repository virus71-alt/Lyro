import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:on_audio_query/on_audio_query.dart';

import 'artwork_cache.dart';

/// Dominant colour of a track's embedded artwork, used to tint the queue.
class ArtworkTint {
  static const Color fallback = Color(0xFF16171B);

  static final Map<int, Color?> _cache = {};

  /// Saturation-weighted average colour of the artwork, or null without art.
  static Future<Color?> of(int songId) async {
    if (_cache.containsKey(songId)) return _cache[songId];
    Color? color;
    try {
      final bytes = await ArtworkCache.load(songId, ArtworkType.AUDIO, 160);
      if (bytes != null && bytes.isNotEmpty) {
        final codec = await ui.instantiateImageCodec(
          bytes,
          targetWidth: 16,
          targetHeight: 16,
        );
        final image = (await codec.getNextFrame()).image;
        final data = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
        if (data != null) color = _dominant(data);
      }
    } catch (_) {
      color = null;
    }
    _cache[songId] = color;
    return color;
  }

  static Color _dominant(ByteData data) {
    double r = 0, g = 0, b = 0, total = 0;
    for (var i = 0; i + 3 < data.lengthInBytes; i += 4) {
      final pr = data.getUint8(i) / 255;
      final pg = data.getUint8(i + 1) / 255;
      final pb = data.getUint8(i + 2) / 255;
      final mx = math.max(pr, math.max(pg, pb));
      final mn = math.min(pr, math.min(pg, pb));
      final sat = mx == 0 ? 0.0 : (mx - mn) / mx;
      final w = 0.05 + sat * mx; // favour vivid, non-black pixels
      r += pr * w;
      g += pg * w;
      b += pb * w;
      total += w;
    }
    return Color.from(
      alpha: 1,
      red: r / total,
      green: g / total,
      blue: b / total,
    );
  }

  /// Deep, muted background shade of [color] (keeps text readable).
  static Color background(Color? color, {double lift = 0}) {
    if (color == null) {
      return Color.lerp(fallback, Colors.white, lift * 1.5)!;
    }
    final hsl = HSLColor.fromColor(color);
    return hsl
        .withSaturation(math.min(hsl.saturation, 0.42))
        .withLightness(0.12 + lift)
        .toColor();
  }
}

/// Loads the artwork tint for [songId] and cross-fades between tints.
class ArtworkTintBuilder extends StatefulWidget {
  final int songId;
  final Widget Function(BuildContext context, Color? tint) builder;

  const ArtworkTintBuilder({
    super.key,
    required this.songId,
    required this.builder,
  });

  @override
  State<ArtworkTintBuilder> createState() => _ArtworkTintBuilderState();
}

class _ArtworkTintBuilderState extends State<ArtworkTintBuilder> {
  Color? _tint;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(covariant ArtworkTintBuilder oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.songId != widget.songId) _load();
  }

  Future<void> _load() async {
    final id = widget.songId;
    if (id <= 0) return;
    final tint = await ArtworkTint.of(id);
    if (mounted && id == widget.songId) setState(() => _tint = tint);
  }

  @override
  Widget build(BuildContext context) {
    // The tween must always have a non-null end, so animate from/to the
    // fallback shade and tell the builder "no tint" until one has loaded.
    // (Same widget shape either way, so the subtree keeps its state.)
    final hasTint = _tint != null;
    return TweenAnimationBuilder<Color?>(
      tween: ColorTween(end: _tint ?? ArtworkTint.fallback),
      duration: const Duration(milliseconds: 500),
      builder: (context, tint, _) => widget.builder(context, hasTint ? tint : null),
    );
  }
}
