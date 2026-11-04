import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/physics.dart';
import 'package:flutter/services.dart';

import '../../../data/models/song_model.dart';
import 'cmf_speaker_3d.dart';
import 'speaker_grille_painter.dart';
import 'track_artwork.dart';

/// Interactive real-time 3D CMF speaker for Lyro.
///
/// - Procedural 3D model (see [cmf_speaker_3d.dart]) rendered every frame with
///   perspective projection, back-face culling and per-vertex studio lighting.
/// - Free-hand orbit: drag to turn it any way you like; a fling keeps it
///   spinning with friction and it stays where it stops. Double-tap the
///   speaker to glide back to the resting three-quarter view.
/// - While playing, sound waves pulse out of the side passive radiators.
/// - Red bar knob: tap to play/pause; it depresses, twists, and turns a
///   quarter turn between the paused and playing positions.
/// - Rotary dial: sleep timer. Each tap turns it one notch (15 / 30 / 60 min,
///   end of track, off); a ring of red dots around it shows the time left.
/// - Colourways and reduce-motion (no spinning art or sound waves).
/// - Album artwork spins inside the recessed front aperture with parallax.
class LyroSpeakerPlayer extends StatefulWidget {
  final Song song;
  final bool isPlaying;
  final VoidCallback onPlayPause;
  final double width;

  /// Sleep timer: dial notch (0 = off), remaining 0..1 for the dot ring
  /// (null when off or "end of track"), and a spoken description.
  final int sleepStep;
  final double? sleepFraction;
  final String sleepLabel;
  final VoidCallback onDialTap;

  final Color bodyColor;
  final Color knobColor;
  final Color dialColor;
  final Color logoColor;
  final bool reduceMotion;

  /// Which hardware design to show.
  final SpeakerShape shape;

  /// Box height; defaults to `width * 1.3`. The model is scaled to fit.
  final double? height;

  const LyroSpeakerPlayer({
    super.key,
    required this.song,
    required this.isPlaying,
    required this.onPlayPause,
    required this.onDialTap,
    this.sleepStep = 0,
    this.sleepFraction,
    this.sleepLabel = 'Sleep timer off',
    this.bodyColor = SpeakerGeometry.bodyColor,
    this.knobColor = SpeakerGeometry.knobColor,
    this.dialColor = SpeakerGeometry.dialColor,
    this.logoColor = const Color(0xB8FFFFFF),
    this.reduceMotion = false,
    this.shape = SpeakerShape.classic,
    this.width = 280.0,
    this.height,
  });

  @override
  State<LyroSpeakerPlayer> createState() => _LyroSpeakerPlayerState();
}

class _LyroSpeakerPlayerState extends State<LyroSpeakerPlayer>
    with TickerProviderStateMixin {
  static const double _restYaw = 0.5;
  static const double _restPitch = 0.3;
  static const double _minPitch = -0.15;
  static const double _maxPitch = 1.2;
  static const _spring = SpringDescription(mass: 1, stiffness: 60, damping: 14);
  static const _tapSlop = 12.0;
  static const _doubleTapWindow = Duration(milliseconds: 320);

  // Continuous spinning artwork animation (pauses/resumes at exact angle)
  late final AnimationController _artworkRotationController;

  // Orbit angles
  late final AnimationController _yawController;
  late final AnimationController _pitchController;

  // Knob press, knob play-position and sleep-timer dial
  late final AnimationController _knobPressController;
  late final Animation<double> _knobPressAnimation;
  late final AnimationController _knobTurnController;
  late final AnimationController _dialRotationController;

  // Static grille overlay, built once so it never repaints.
  final Widget _grille = CustomPaint(
    painter: SpeakerGrillePainter(isDark: true, opacity: 0.22),
  );

  // Sound waves from the side radiators
  late final AnimationController _wavePhaseController;
  late final AnimationController _waveLevelController;

  // Double-tap detection (raw pointers, so knob taps stay instant)
  Offset? _downPosition;
  Duration? _downTime;
  Offset? _lastTapPosition;
  Duration? _lastTapTime;
  Rect _knobRect = Rect.zero;
  Rect _dialRect = Rect.zero;

  @override
  void initState() {
    super.initState();

    _artworkRotationController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 15),
    );

    _yawController = AnimationController.unbounded(vsync: this, value: _restYaw)
      ..addStatusListener((status) {
        // Keep the angle bounded once a spin settles.
        if (status == AnimationStatus.completed) {
          final twoPi = math.pi * 2;
          _yawController.value =
              _restYaw + (_yawController.value - _restYaw) % twoPi;
          if ((_yawController.value - _restYaw - twoPi).abs() < 1e-3) {
            _yawController.value = _restYaw;
          }
        }
      });
    _pitchController = AnimationController.unbounded(
      vsync: this,
      value: _restPitch,
    );

    _knobPressController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 120),
      reverseDuration: const Duration(milliseconds: 260),
    );
    _knobPressAnimation = CurvedAnimation(
      parent: _knobPressController,
      curve: Curves.easeInQuad,
      reverseCurve: Curves.easeOutBack,
    );

    _knobTurnController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 320),
      value: widget.isPlaying ? 1.0 : 0.0,
    );

    _dialRotationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 250),
      value: widget.sleepStep / 4,
    );

    _wavePhaseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1800),
    );
    _waveLevelController =
        AnimationController(
          vsync: this,
          duration: const Duration(milliseconds: 450),
        )..addStatusListener((status) {
          if (status == AnimationStatus.dismissed) _wavePhaseController.stop();
        });
    _syncMotion(initial: true);
  }

  /// Spinning art and sound waves run only while playing, unless motion is
  /// reduced.
  void _syncMotion({bool initial = false}) {
    final animate = widget.isPlaying && !widget.reduceMotion;
    if (animate) {
      if (!_artworkRotationController.isAnimating) {
        _artworkRotationController.repeat();
      }
      if (!_wavePhaseController.isAnimating) _wavePhaseController.repeat();
      if (initial) {
        _waveLevelController.value = 1.0;
      } else {
        _waveLevelController.forward();
      }
    } else {
      _artworkRotationController.stop(canceled: false);
      if (initial) {
        _waveLevelController.value = 0.0;
      } else {
        _waveLevelController.reverse();
      }
    }
  }

  @override
  void didUpdateWidget(covariant LyroSpeakerPlayer oldWidget) {
    super.didUpdateWidget(oldWidget);

    if (widget.isPlaying != oldWidget.isPlaying) {
      _knobTurnController.animateTo(
        widget.isPlaying ? 1.0 : 0.0,
        curve: Curves.easeOutBack,
      );
    }
    if (widget.isPlaying != oldWidget.isPlaying ||
        widget.reduceMotion != oldWidget.reduceMotion) {
      _syncMotion();
    }

    if (widget.sleepStep != oldWidget.sleepStep) {
      _dialRotationController.animateTo(
        widget.sleepStep / 4,
        curve: Curves.easeOutBack,
      );
    }
  }

  @override
  void dispose() {
    _artworkRotationController.dispose();
    _yawController.dispose();
    _pitchController.dispose();
    _knobPressController.dispose();
    _knobTurnController.dispose();
    _dialRotationController.dispose();
    _wavePhaseController.dispose();
    _waveLevelController.dispose();
    super.dispose();
  }

  void _onPanStart(DragStartDetails details) {
    _yawController.stop();
    _pitchController.stop();
  }

  double get _dragGain => 2.6 / widget.width;

  void _onPanUpdate(DragUpdateDetails details) {
    _yawController.value += details.delta.dx * _dragGain;
    _pitchController.value =
        (_pitchController.value + details.delta.dy * _dragGain * 0.6).clamp(
          _minPitch,
          _maxPitch,
        );
  }

  void _onPanEnd(DragEndDetails details) {
    // Free-hand: a fling coasts to a stop and the speaker stays there.
    final velocity = details.velocity.pixelsPerSecond.dx * _dragGain;
    if (velocity.abs() < 0.05) return;
    _yawController.animateWith(
      FrictionSimulation(
        0.12,
        _yawController.value,
        velocity,
        tolerance: const Tolerance(velocity: 0.02),
      ),
    );
  }

  /// Glides back to the resting three-quarter view by the shortest turn.
  void _resetView() {
    HapticFeedback.selectionClick();
    final twoPi = math.pi * 2;
    final yaw = _yawController.value;
    final target = _restYaw + ((yaw - _restYaw) / twoPi).round() * twoPi;
    _yawController.animateWith(SpringSimulation(_spring, yaw, target, 0));
    _pitchController.animateWith(
      SpringSimulation(_spring, _pitchController.value, _restPitch, 0),
    );
  }

  void _onPointerDown(PointerDownEvent e) {
    _downPosition = e.localPosition;
    _downTime = e.timeStamp;
  }

  void _onPointerUp(PointerUpEvent e) {
    final down = _downPosition, downTime = _downTime;
    _downPosition = null;
    if (down == null || downTime == null) return;
    final isTap =
        (e.localPosition - down).distance < _tapSlop &&
        e.timeStamp - downTime < const Duration(milliseconds: 300);
    if (!isTap ||
        _knobRect.contains(e.localPosition) ||
        _dialRect.contains(e.localPosition)) {
      _lastTapTime = null;
      return;
    }
    final last = _lastTapTime, lastPos = _lastTapPosition;
    if (last != null &&
        lastPos != null &&
        e.timeStamp - last < _doubleTapWindow &&
        (e.localPosition - lastPos).distance < 48) {
      _lastTapTime = null;
      _resetView();
    } else {
      _lastTapTime = e.timeStamp;
      _lastTapPosition = e.localPosition;
    }
  }

  @override
  Widget build(BuildContext context) {
    final w = widget.width;
    final h = widget.height ?? w * 1.3;

    return Center(
      child: Listener(
        onPointerDown: _onPointerDown,
        onPointerUp: _onPointerUp,
        child: GestureDetector(
          behavior: HitTestBehavior.translucent,
          onPanStart: _onPanStart,
          onPanUpdate: _onPanUpdate,
          onPanEnd: _onPanEnd,
          child: SizedBox(
            width: w,
            height: h,
            child: AnimatedBuilder(
              animation: Listenable.merge([
                _yawController,
                _pitchController,
                _knobPressAnimation,
                _knobTurnController,
                _dialRotationController,
              ]),
              builder: (context, _) => _buildScene(w, h),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildScene(double w, double h) {
    final shape = widget.shape;
    SpeakerGeometry.active = shape;
    final camera = SpeakerCamera(
      yaw: _yawController.value,
      pitch: _pitchController.value,
      // Fits the model plus room for the sound waves on either side.
      scale: math.min(w / shape.fitWidth, h / shape.fitHeight),
      center: Offset(w / 2, h * 0.5),
      pivotY: shape.pivotY,
    );
    final pose = SpeakerPose(
      camera: camera,
      knobPress: _knobPressAnimation.value,
      knobTurn: _knobTurnController.value,
      dialTurn: _dialRotationController.value,
      dialLit: widget.sleepStep > 0,
      sleepFraction: widget.sleepFraction,
      body: widget.bodyColor,
      knob: widget.knobColor,
      dial: widget.dialColor,
      shape: shape,
    );

    final knobCenter = camera.screen(pose.knobTop);
    final knobSize = camera.scaleAt(pose.knobTop) * 0.3 * shape.knobScale;
    final dialCenter = camera.screen(pose.dialTop);
    final dialSize = camera.scaleAt(pose.dialTop) * 0.22 * math.max(shape.dialScale, 0.75);
    final controlsLive = pose.roofVisible;
    _knobRect = Rect.fromCenter(
      center: knobCenter,
      width: knobSize,
      height: knobSize,
    );
    _dialRect = Rect.fromCenter(
      center: dialCenter,
      width: dialSize,
      height: dialSize,
    );

    SpeakerSoundWavePainter waves({required bool near}) =>
        SpeakerSoundWavePainter(
          camera: camera,
          phase: _wavePhaseController,
          level: _waveLevelController,
          nearSides: near,
          color: Theme.of(context).brightness == Brightness.dark
              ? const Color(0xFFEDEDED)
              : const Color(0xFF3A3A3A),
          shape: shape,
        );

    return Stack(
      clipBehavior: Clip.none,
      children: [
        Positioned.fill(
          child: IgnorePointer(
            child: RepaintBoundary(
              child: CustomPaint(painter: waves(near: false)),
            ),
          ),
        ),
        Positioned.fill(
          child: RepaintBoundary(
            child: CustomPaint(painter: SpeakerBackPainter(pose)),
          ),
        ),
        Positioned.fill(
          child: IgnorePointer(
            child: RepaintBoundary(
              child: CustomPaint(painter: waves(near: true)),
            ),
          ),
        ),
        if (SpeakerFrontFace.visibility(camera, shape) > 0.02)
          Positioned(
            left: 0,
            top: 0,
            child: IgnorePointer(
              child: SpeakerFrontFace(
                camera: camera,
                artwork: (diameter) {
                  final art = TrackArtwork(
                    key: ValueKey('speaker_art_${widget.song.id}'),
                    songId: widget.song.id,
                    title: widget.song.title,
                    size: diameter,
                    radius: shape.screen ? 0 : diameter / 2,
                  );
                  return shape.screen
                      ? art
                      : RotationTransition(turns: _artworkRotationController, child: art);
                },
                grille: shape.screen ? const SizedBox.shrink() : _grille,
                logoColor: widget.logoColor,
                shape: shape,
              ),
            ),
          ),
        Positioned.fill(
          child: IgnorePointer(
            child: RepaintBoundary(
              child: CustomPaint(painter: SpeakerFrontPainter(pose)),
            ),
          ),
        ),

        // Touch targets tracking the projected controls
        Positioned(
          left: dialCenter.dx - dialSize / 2,
          top: dialCenter.dy - dialSize / 2,
          width: dialSize,
          height: dialSize,
          child: IgnorePointer(
            ignoring: !controlsLive,
            child: Semantics(
              button: true,
              label: 'Sleep timer dial',
              value: widget.sleepLabel,
              hint: 'Turns to the next sleep time',
              child: GestureDetector(
                key: const Key('speaker_sleep_dial'),
                behavior: HitTestBehavior.opaque,
                onTap: () {
                  HapticFeedback.selectionClick();
                  widget.onDialTap();
                },
              ),
            ),
          ),
        ),
        Positioned(
          left: knobCenter.dx - knobSize / 2,
          top: knobCenter.dy - knobSize / 2,
          width: knobSize,
          height: knobSize,
          child: IgnorePointer(
            ignoring: !controlsLive,
            child: Semantics(
              button: true,
              label: widget.isPlaying ? 'Pause' : 'Play',
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
                onTapCancel: () => _knobPressController.reverse(),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
