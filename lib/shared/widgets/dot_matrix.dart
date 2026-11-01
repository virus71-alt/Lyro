import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' show PointMode;

import 'package:flutter/material.dart';

import '../../core/theme/nothing_colors.dart';

/// 5×7 dot-matrix glyphs (rows top to bottom, `#` = lit dot).
class DotFont {
  DotFont._();

  static const int cols = 5;
  static const int rows = 7;

  static const Map<String, List<String>> glyphs = {
    'A': ['.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
    'B': ['####.', '#...#', '#...#', '####.', '#...#', '#...#', '####.'],
    'C': ['.###.', '#...#', '#....', '#....', '#....', '#...#', '.###.'],
    'D': ['####.', '#...#', '#...#', '#...#', '#...#', '#...#', '####.'],
    'E': ['#####', '#....', '#....', '####.', '#....', '#....', '#####'],
    'F': ['#####', '#....', '#....', '####.', '#....', '#....', '#....'],
    'G': ['.###.', '#...#', '#....', '#.###', '#...#', '#...#', '.####'],
    'H': ['#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
    'I': ['.###.', '..#..', '..#..', '..#..', '..#..', '..#..', '.###.'],
    'J': ['..###', '...#.', '...#.', '...#.', '...#.', '#..#.', '.##..'],
    'K': ['#...#', '#..#.', '#.#..', '##...', '#.#..', '#..#.', '#...#'],
    'L': ['#....', '#....', '#....', '#....', '#....', '#....', '#####'],
    'M': ['#...#', '##.##', '#.#.#', '#.#.#', '#...#', '#...#', '#...#'],
    'N': ['#...#', '#...#', '##..#', '#.#.#', '#..##', '#...#', '#...#'],
    'O': ['.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
    'P': ['####.', '#...#', '#...#', '####.', '#....', '#....', '#....'],
    'Q': ['.###.', '#...#', '#...#', '#...#', '#.#.#', '#..#.', '.##.#'],
    'R': ['####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'],
    'S': ['.####', '#....', '#....', '.###.', '....#', '....#', '####.'],
    'T': ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '..#..'],
    'U': ['#...#', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
    'V': ['#...#', '#...#', '#...#', '#...#', '#...#', '.#.#.', '..#..'],
    'W': ['#...#', '#...#', '#...#', '#.#.#', '#.#.#', '#.#.#', '.#.#.'],
    'X': ['#...#', '#...#', '.#.#.', '..#..', '.#.#.', '#...#', '#...#'],
    'Y': ['#...#', '#...#', '.#.#.', '..#..', '..#..', '..#..', '..#..'],
    'Z': ['#####', '....#', '...#.', '..#..', '.#...', '#....', '#####'],
  };

  /// Lit dot positions (column, row) for [text], one blank column between
  /// letters. Unknown characters render as a blank cell.
  static List<math.Point<int>> layout(String text) {
    final dots = <math.Point<int>>[];
    var x = 0;
    for (final ch in text.toUpperCase().split('')) {
      final g = glyphs[ch];
      if (g != null) {
        for (var r = 0; r < rows; r++) {
          for (var c = 0; c < cols; c++) {
            if (g[r][c] == '#') dots.add(math.Point(x + c, r));
          }
        }
      }
      x += cols + 1;
    }
    return dots;
  }

  static int widthOf(String text) =>
      text.isEmpty ? 0 : text.length * (cols + 1) - 1;
}

// =============================================================================
// ANIMATED LOGO
// =============================================================================

/// Dot-matrix wordmark that comes alive with playback.
///
/// Playing: the letters ride a travelling wave (dots rise and brighten in a
/// moving crest, like sound passing through). Paused: a Glyph-style light
/// sweep runs across the dimmed dots with occasional sparkle.
class DotMatrixLogo extends StatefulWidget {
  final String text;
  final bool isPlaying;
  final double dotPitch;
  final Color color;

  const DotMatrixLogo({
    super.key,
    this.text = 'LYRO',
    required this.isPlaying,
    this.dotPitch = 3.4,
    this.color = NothingColors.darkDisplay,
  });

  @override
  State<DotMatrixLogo> createState() => _DotMatrixLogoState();
}

class _DotMatrixLogoState extends State<DotMatrixLogo>
    with TickerProviderStateMixin {
  late final AnimationController _clock = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 60),
  )..repeat();

  // Cross-fade between the two effects
  late final AnimationController _mode = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 500),
    value: widget.isPlaying ? 1 : 0,
  );

  @override
  void didUpdateWidget(covariant DotMatrixLogo oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isPlaying != oldWidget.isPlaying) {
      widget.isPlaying ? _mode.forward() : _mode.reverse();
    }
  }

  @override
  void dispose() {
    _clock.dispose();
    _mode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final w = DotFont.widthOf(widget.text) * widget.dotPitch;
    // Extra headroom for the wave's vertical travel
    final h = (DotFont.rows + 2) * widget.dotPitch;
    return RepaintBoundary(
      child: CustomPaint(
        size: Size(w, h),
        painter: _DotLogoPainter(
          dots: DotFont.layout(widget.text),
          columns: DotFont.widthOf(widget.text),
          pitch: widget.dotPitch,
          color: widget.color,
          clock: _clock,
          mode: _mode,
        ),
      ),
    );
  }
}

class _DotLogoPainter extends CustomPainter {
  final List<math.Point<int>> dots;
  final int columns;
  final double pitch;
  final Color color;
  final Animation<double> clock;
  final Animation<double> mode;

  _DotLogoPainter({
    required this.dots,
    required this.columns,
    required this.pitch,
    required this.color,
    required this.clock,
    required this.mode,
  }) : super(repaint: Listenable.merge([clock, mode]));

  static double _hash(int a, int b) {
    final v = math.sin(a * 12.9898 + b * 78.233) * 43758.5453;
    return v - v.floorToDouble();
  }

  @override
  void paint(Canvas canvas, Size size) {
    final t = clock.value * 60; // seconds
    final m = Curves.easeInOut.transform(mode.value);
    final top = pitch;
    final r = pitch * 0.36;

    // Bucket dots by brightness so each bucket is one batched draw
    const buckets = 6;
    final pts = List.generate(buckets, (_) => <double>[]);

    for (final d in dots) {
      final x = d.x * pitch + pitch / 2;
      var y = top + d.y * pitch + pitch / 2;

      // Playing: travelling wave lifts and brightens each column in turn
      final phase = d.x / columns * math.pi * 2;
      final wave = math.sin(t * 5.2 - phase * 1.6);
      y -= m * wave * pitch * 0.85;
      final waveLevel = 0.55 + 0.45 * (wave * 0.5 + 0.5);

      // Paused: Glyph light sweep + sparse sparkle on dimmed dots
      final sweepPos = (t * 0.45) % 1.6 - 0.3; // -0.3..1.3 across the word
      final dist = (d.x / columns - sweepPos).abs();
      final sweep = math.exp(-dist * dist / 0.012);
      final tick = (t * 2.5).floor();
      final sparkle = _hash(d.x * 7 + d.y, tick) > 0.96 ? 0.6 : 0.0;
      final glyphLevel = 0.28 + 0.72 * math.max(sweep, sparkle);

      final level = glyphLevel * (1 - m) + waveLevel * m;
      final b = (level * (buckets - 1)).round().clamp(0, buckets - 1);
      pts[b]..add(x)..add(y);
    }

    for (var b = 0; b < buckets; b++) {
      if (pts[b].isEmpty) continue;
      canvas.drawRawPoints(
        PointMode.points,
        Float32List.fromList(pts[b]),
        Paint()
          ..color = color.withValues(alpha: 0.15 + 0.85 * b / (buckets - 1))
          ..strokeWidth = r * 2
          ..strokeCap = StrokeCap.round,
      );
    }
  }

  @override
  bool shouldRepaint(_DotLogoPainter old) =>
      old.dots != dots || old.color != color || old.pitch != pitch;
}

// =============================================================================
// AVATARS
// =============================================================================

/// Pre-made Nothing-style dot-matrix avatars (11×11; `#` white, `R` red).
/// Index 0 is a monogram built from the user's initial.
class NothingAvatars {
  NothingAvatars._();

  static const int size = 11;

  static const List<String> names = [
    'Monogram', 'Smile', 'Headphones', 'Note', 'Vinyl', 'Speaker',
    'Equalizer', 'Heart', 'Spark', 'Cat', 'Ghost', 'Bot',
  ];

  static int get count => names.length;

  static const List<List<String>> _art = [
    [], // monogram — generated
    [
      '...#####...',
      '..#.....#..',
      '.#.......#.',
      '#..R...R..#',
      '#..R...R..#',
      '#.........#',
      '#..#...#..#',
      '.#..###..#.',
      '..#.....#..',
      '...#####...',
      '...........',
    ],
    [
      '...#####...',
      '..#.....#..',
      '.#.......#.',
      '#.........#',
      '#.........#',
      '##.......##',
      '#RR.....RR#',
      '#RR.....RR#',
      '#RR.....RR#',
      '##.......##',
      '...........',
    ],
    [
      '......#....',
      '......##...',
      '......#.#..',
      '......#..#.',
      '......#..#.',
      '......#.#..',
      '......#....',
      '...RRR#....',
      '..RRRRR....',
      '..RRRRR....',
      '...RRR.....',
    ],
    [
      '...#####...',
      '.##.....##.',
      '.#..###..#.',
      '#..#...#..#',
      '#.#..R..#.#',
      '#.#.RRR.#.#',
      '#.#..R..#.#',
      '#..#...#..#',
      '.#..###..#.',
      '.##.....##.',
      '...#####...',
    ],
    [
      '......RR...',
      '......RR...',
      '.#########.',
      '.#.......#.',
      '.#..###..#.',
      '.#.#...#.#.',
      '.#.#.#.#.#.',
      '.#.#...#.#.',
      '.#..###..#.',
      '.#.......#.',
      '.#########.',
    ],
    [
      '...........',
      '........#..',
      '..#.....#..',
      '..#..#..#..',
      '..#..#..#.#',
      '#.#..#..#.#',
      '#.#.##..#.#',
      '#.#.##.##.#',
      '#.#.##.##.#',
      '#.#.##.##.#',
      'RRRRRRRRRRR',
    ],
    [
      '...........',
      '.###...###.',
      '#RRR#.#RRR#',
      '#RRRR#RRRR#',
      '#RRRRRRRRR#',
      '.#RRRRRRR#.',
      '..#RRRRR#..',
      '...#RRR#...',
      '....#R#....',
      '.....#.....',
      '...........',
    ],
    [
      '.....#.....',
      '.....#.....',
      '....#R#....',
      '....#R#....',
      '..##RRR##..',
      '##RRRRRRR##',
      '..##RRR##..',
      '....#R#....',
      '....#R#....',
      '.....#.....',
      '.....#.....',
    ],
    [
      '#.........#',
      '##.......##',
      '#.#######.#',
      '#.........#',
      '#..R...R..#',
      '#.........#',
      '#....#....#',
      '#..#.#.#..#',
      '.#..#.#..#.',
      '..#.....#..',
      '...#####...',
    ],
    [
      '...#####...',
      '..#.....#..',
      '.#.......#.',
      '.#.R...R.#.',
      '.#.RR.RR.#.',
      '.#.......#.',
      '.#.......#.',
      '.#.......#.',
      '.#.......#.',
      '.#.#.#.#.#.',
      '.##.#.#.##.',
    ],
    [
      '.....R.....',
      '.....#.....',
      '.#########.',
      '.#.......#.',
      '.#.RR.RR.#.',
      '.#.RR.RR.#.',
      '.#.......#.',
      '.#..###..#.',
      '.#.......#.',
      '.#########.',
      '..#.....#..',
    ],
  ];

  /// Grid for avatar [id] (`#`, `R` or `.` per cell).
  static List<String> grid(int id, String initial) {
    if (id > 0 && id < _art.length) return _art[id];
    // Monogram: ring of dots, initial in the middle, red accent dot
    final cells = List.generate(size, (_) => List.filled(size, '.'));
    const c = (size - 1) / 2;
    for (var y = 0; y < size; y++) {
      for (var x = 0; x < size; x++) {
        final d = math.sqrt(math.pow(x - c, 2) + math.pow(y - c, 2));
        if ((d - 5).abs() < 0.5) cells[y][x] = '#';
      }
    }
    final g = DotFont.glyphs[initial.toUpperCase()] ?? DotFont.glyphs['L']!;
    for (var r = 0; r < DotFont.rows; r++) {
      for (var col = 0; col < DotFont.cols; col++) {
        if (g[r][col] == '#') cells[2 + r][3 + col] = 'R';
      }
    }
    return [for (final row in cells) row.join()];
  }
}

/// Circular dot-matrix avatar.
class NothingAvatar extends StatelessWidget {
  final int avatarId;
  final String name;
  final double size;
  final bool selected;

  const NothingAvatar({
    super.key,
    required this.avatarId,
    required this.name,
    this.size = 72,
    this.selected = false,
  });

  @override
  Widget build(BuildContext context) {
    final initial = name.trim().isEmpty ? 'L' : name.trim()[0];
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: const Color(0xFF0E0E0E),
        border: Border.all(
          color: selected ? NothingColors.signalRed : NothingColors.darkLine2,
          width: selected ? 2 : 1.2,
        ),
      ),
      child: CustomPaint(
        painter: _AvatarPainter(NothingAvatars.grid(avatarId, initial)),
      ),
    );
  }
}

class _AvatarPainter extends CustomPainter {
  final List<String> grid;
  _AvatarPainter(this.grid);

  @override
  void paint(Canvas canvas, Size size) {
    const n = NothingAvatars.size;
    final pitch = size.width * 0.72 / n;
    final origin = (size.width - pitch * n) / 2 + pitch / 2;
    final off = <double>[], white = <double>[], red = <double>[];
    for (var y = 0; y < n; y++) {
      for (var x = 0; x < n; x++) {
        final px = origin + x * pitch, py = origin + y * pitch;
        switch (grid[y][x]) {
          case '#':
            white..add(px)..add(py);
          case 'R':
            red..add(px)..add(py);
          default:
            off..add(px)..add(py);
        }
      }
    }
    void dots(List<double> pts, Color color, double scale) {
      if (pts.isEmpty) return;
      canvas.drawRawPoints(
        PointMode.points,
        Float32List.fromList(pts),
        Paint()
          ..color = color
          ..strokeWidth = pitch * scale
          ..strokeCap = StrokeCap.round,
      );
    }

    dots(off, const Color(0xFF1E1E1E), 0.55);
    dots(white, NothingColors.darkDisplay, 0.72);
    dots(red, NothingColors.signalRed, 0.72);
  }

  @override
  bool shouldRepaint(_AvatarPainter old) => old.grid != grid;
}
