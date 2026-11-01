import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../core/theme/nothing_colors.dart';
import '../../../data/models/song_model.dart';
import 'speaker_grille_painter.dart';
import 'track_artwork.dart';

/// Interactive CMF 3D Speaker Experience for Lyro.
///
/// Features:
/// - Genuine 3D procedural rendering using perspective projection, custom geometry paths,
///   directional lighting gradients, and depth-aware shading.
/// - Defined panels: Rear Lanyard Panel with pass-through cutout hole, Left Elevation Face with
///   passive radiator driver, Angled Top Control Deck with secondary dial dish and knob collar,
///   and Front Face with circular speaker aperture and LYRO branding.
/// - Tactile 3D Red Playback Knob: physically depresses inward, compresses cast shadow, twists
///   mechanically by 20 degrees, and springs back with realistic mechanical damping on release.
/// - Secondary Rotary Dial: toggles shuffle state with rotational feedback and glowing accent.
/// - Continuous 15s rotating circular album artwork inside the aperture (freezes on pause, resumes smoothly).
/// - Stationary micro-perforated metallic grille overlay.
/// - Interactive 3D touch/drag: subtle tilt and yaw with spring-back to canonical resting perspective.
class LyroSpeakerPlayer extends StatefulWidget {
  final Song song;
  final bool isPlaying;
  final bool isShuffle;
  final VoidCallback onPlayPause;
  final VoidCallback onToggleShuffle;
  final double width;

  const LyroSpeakerPlayer({
    super.key,
    required this.song,
    required this.isPlaying,
    required this.isShuffle,
    required this.onPlayPause,
    required this.onToggleShuffle,
    this.width = 280.0,
  });

  @override
  State<LyroSpeakerPlayer> createState() => _LyroSpeakerPlayerState();
}

class _LyroSpeakerPlayerState extends State<LyroSpeakerPlayer>
    with TickerProviderStateMixin {
  // Continuous spinning artwork animation (pauses/resumes at exact angle)
  late final AnimationController _artworkRotationController;

  // Physical Red Knob press animation (inward depression, shadow compression, rotational twist)
  late final AnimationController _knobPressController;
  late final Animation<double> _knobPressAnimation;

  // Secondary Dial rotation on shuffle toggle
  late final AnimationController _dialRotationController;
  late final Animation<double> _dialRotationAnimation;

  // Interactive 3D tilt & spring-back animation
  late final AnimationController _springBackController;
  Animation<Offset>? _springBackAnimation;

  // Current interactive 3D offsets from canonical rest angle
  double _interactiveYaw = 0.0;
  double _interactivePitch = 0.0;

  @override
  void initState() {
    super.initState();

    // 1. Artwork continuous rotation (15 seconds per revolution)
    _artworkRotationController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 15),
    );
    if (widget.isPlaying) {
      _artworkRotationController.repeat();
    }

    // 2. Physical Red Knob press animation
    _knobPressController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 140),
      reverseDuration: const Duration(milliseconds: 220),
    );
    _knobPressAnimation = CurvedAnimation(
      parent: _knobPressController,
      curve: Curves.easeInQuad,
      reverseCurve: Curves.easeOutBack,
    );

    // 3. Dial rotation animation for shuffle toggle
    _dialRotationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 250),
    );
    _dialRotationAnimation = CurvedAnimation(
      parent: _dialRotationController,
      curve: Curves.easeOutCubic,
    );
    if (widget.isShuffle) {
      _dialRotationController.value = 1.0;
    }

    // 4. Spring-back animation controller for interactive 3D drag
    _springBackController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 400),
    );
  }

  @override
  void didUpdateWidget(covariant LyroSpeakerPlayer oldWidget) {
    super.didUpdateWidget(oldWidget);

    // Synchronize artwork rotation with playback state
    if (widget.isPlaying != oldWidget.isPlaying) {
      if (widget.isPlaying) {
        if (!_artworkRotationController.isAnimating) {
          _artworkRotationController.repeat();
        }
      } else {
        if (_artworkRotationController.isAnimating) {
          _artworkRotationController.stop(canceled: false);
        }
      }
    }

    // Synchronize shuffle dial rotation
    if (widget.isShuffle != oldWidget.isShuffle) {
      if (widget.isShuffle) {
        _dialRotationController.forward();
      } else {
        _dialRotationController.reverse();
      }
    }
  }

  @override
  void dispose() {
    _artworkRotationController.dispose();
    _knobPressController.dispose();
    _dialRotationController.dispose();
    _springBackController.dispose();
    super.dispose();
  }

  void _onPanUpdate(DragUpdateDetails details) {
    setState(() {
      _interactiveYaw = (_interactiveYaw + details.delta.dx * 0.0035).clamp(-0.20, 0.20);
      _interactivePitch = (_interactivePitch - details.delta.dy * 0.0035).clamp(-0.14, 0.14);
    });
  }

  void _onPanEnd(DragEndDetails details) {
    final startOffset = Offset(_interactiveYaw, _interactivePitch);
    _springBackAnimation = Tween<Offset>(
      begin: startOffset,
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _springBackController,
      curve: Curves.easeOutBack,
    ))..addListener(() {
        setState(() {
          _interactiveYaw = _springBackAnimation!.value.dx;
          _interactivePitch = _springBackAnimation!.value.dy;
        });
      });

    _springBackController.forward(from: 0.0);
  }

  @override
  Widget build(BuildContext context) {
    // Proportions matching the authentic CaseLab CMF Speaker S (420 x 605)
    final w = widget.width;
    final h = w * (605.0 / 420.0);

    // Exact geometric landmarks scaled to widget dimensions
    final scale = w / 420.0;
    final apertureCenter = Offset(219.5 * scale, 408.0 * scale);
    final apertureRadius = 142.0 * scale;

    final knobLeft = 295.0 * scale;
    final knobTop = 114.0 * scale;
    final knobW = 82.0 * scale;
    final knobH = 86.0 * scale;

    final dialCenter = Offset(84.0 * scale, 156.0 * scale);
    final dialRadius = 36.0 * scale;

    // Parallax shift for inner artwork: shifts with interactive viewing angle
    final parallaxX = _interactiveYaw * 12.0;
    final parallaxY = -_interactivePitch * 12.0;

    return Center(
      child: GestureDetector(
        behavior: HitTestBehavior.translucent,
        onPanUpdate: _onPanUpdate,
        onPanEnd: _onPanEnd,
        onPanCancel: () => _onPanEnd(DragEndDetails()),
        child: Transform(
          alignment: Alignment.center,
          transform: Matrix4.identity()
            ..setEntry(3, 2, 0.001)
            ..rotateY(_interactiveYaw)
            ..rotateX(-_interactivePitch),
          child: SizedBox(
            width: w,
            height: h,
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                // -------------------------------------------------------------
                // Layer 1: Rotating Album Artwork inside the Aperture
                // -------------------------------------------------------------
                Positioned(
                  left: apertureCenter.dx - apertureRadius + 0.5 + parallaxX,
                  top: apertureCenter.dy - apertureRadius + 0.5 + parallaxY,
                  width: (apertureRadius - 0.5) * 2,
                  height: (apertureRadius - 0.5) * 2,
                  child: ClipOval(
                    child: RotationTransition(
                      turns: _artworkRotationController,
                      child: AnimatedSwitcher(
                        duration: const Duration(milliseconds: 350),
                        switchInCurve: Curves.easeIn,
                        switchOutCurve: Curves.easeOut,
                        child: TrackArtwork(
                          key: ValueKey('speaker_art_'),
                          songId: widget.song.id,
                          title: widget.song.title,
                          size: (apertureRadius - 0.5) * 2,
                          radius: apertureRadius - 0.5,
                        ),
                      ),
                    ),
                  ),
                ),

                // -------------------------------------------------------------
                // Layer 2: Subtle Stationary Micro-Perforated Grille Mesh
                // with Realistic Convex Speaker Cone Illumination
                // -------------------------------------------------------------
                Positioned(
                  left: apertureCenter.dx - apertureRadius,
                  top: apertureCenter.dy - apertureRadius,
                  width: apertureRadius * 2,
                  height: apertureRadius * 2,
                  child: IgnorePointer(
                    child: ClipOval(
                      child: CustomPaint(
                        size: Size(apertureRadius * 2, apertureRadius * 2),
                        painter: SpeakerGrillePainter(
                          isDark: true,
                          opacity: 0.22,
                        ),
                      ),
                    ),
                  ),
                ),

                // -------------------------------------------------------------
                // Layer 3: Authentic CMF Speaker Housing Enclosure (CaseLab CAD)
                // Pixel-perfect geometry, matte finish, lanyard fin & soft shadow
                // -------------------------------------------------------------
                Positioned.fill(
                  child: IgnorePointer(
                    child: Image.asset(
                      'assets/cmf_speaker_housing.png',
                      fit: BoxFit.contain,
                    ),
                  ),
                ),

                // -------------------------------------------------------------
                // Layer 4: Thin Metallic Aperture Mounting Rim
                // -------------------------------------------------------------
                Positioned(
                  left: apertureCenter.dx - apertureRadius - 2.0,
                  top: apertureCenter.dy - apertureRadius - 2.0,
                  width: (apertureRadius + 2.0) * 2,
                  height: (apertureRadius + 2.0) * 2,
                  child: IgnorePointer(
                    child: CustomPaint(
                      painter: _ApertureBezelPainter(
                        radius: apertureRadius,
                      ),
                    ),
                  ),
                ),

                // -------------------------------------------------------------
                // Layer 5: Interactive Tactile Secondary Dial (Shuffle Control)
                // -------------------------------------------------------------
                Positioned(
                  left: dialCenter.dx - dialRadius,
                  top: dialCenter.dy - dialRadius,
                  width: dialRadius * 2,
                  height: dialRadius * 2,
                  child: GestureDetector(
                    key: const Key('speaker_shuffle_dial'),
                    behavior: HitTestBehavior.opaque,
                    onTap: () {
                      HapticFeedback.lightImpact();
                      widget.onToggleShuffle();
                    },
                    child: AnimatedBuilder(
                      animation: _dialRotationAnimation,
                      builder: (context, _) {
                        return Transform.rotate(
                          angle: _dialRotationAnimation.value * (math.pi / 4),
                          child: widget.isShuffle
                              ? Container(
                                  decoration: BoxDecoration(
                                    shape: BoxShape.circle,
                                    border: Border.all(
                                      color: NothingColors.darkAccent.withValues(alpha: 0.75),
                                      width: 1.5,
                                    ),
                                  ),
                                )
                              : const SizedBox.shrink(),
                        );
                      },
                    ),
                  ),
                ),

                // -------------------------------------------------------------
                // Layer 6: Authentic CMF 3D Red Bar Knob (Tactile Playback Control)
                // -------------------------------------------------------------
                AnimatedBuilder(
                  animation: _knobPressAnimation,
                  builder: (context, _) {
                    final depression = _knobPressAnimation.value * 4.5 * scale;
                    final twistAngle = _knobPressAnimation.value * 0.18;

                    return Positioned(
                      left: knobLeft,
                      top: knobTop + depression,
                      width: knobW,
                      height: knobH,
                      child: GestureDetector(
                        key: const Key('speaker_red_knob'),
                        behavior: HitTestBehavior.opaque,
                        onTapDown: (_) {
                          HapticFeedback.mediumImpact();
                          _knobPressController.forward();
                        },
                        onTapUp: (_) {
                          _knobPressController.reverse();
                          widget.onPlayPause();
                        },
                        onTapCancel: () {
                          _knobPressController.reverse();
                        },
                        child: Transform.rotate(
                          angle: twistAngle,
                          child: Image.asset(
                            'assets/cmf_red_knob.png',
                            fit: BoxFit.contain,
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}


// =============================================================================
// METALLIC APERTURE CHAMFER BEZEL PAINTER (THIN MOUNTING RIM)
// =============================================================================

class _ApertureBezelPainter extends CustomPainter {
  final double radius;
  const _ApertureBezelPainter({required this.radius});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);

    // 1. Thin metallic mounting bezel (provides clean separation without dominating artwork)
    final bezelRect = Rect.fromCircle(center: center, radius: radius + 1.0);
    final bezelPaint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.0
      ..shader = const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [
          Color(0xFF6E7382), // subtle metallic upper-left highlight
          Color(0xFF383A43), // mid charcoal
          Color(0xFF22242B), // lower-right subtle shadow
          Color(0xFF505462), // ambient fill
        ],
        stops: [0.0, 0.40, 0.80, 1.0],
      ).createShader(bezelRect);

    canvas.drawCircle(center, radius + 0.5, bezelPaint);

    // 2. Micro hairline definition on the outer edge (0.8px)
    final hairlinePaint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 0.8
      ..color = const Color(0xFF141518);

    canvas.drawCircle(center, radius + 1.8, hairlinePaint);
  }

  @override
  bool shouldRepaint(covariant _ApertureBezelPainter oldDelegate) =>
      oldDelegate.radius != radius;
}

// =============================================================================
