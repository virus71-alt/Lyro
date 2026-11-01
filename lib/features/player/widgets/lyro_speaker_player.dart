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
/// - Rotary dial: tap to toggle shuffle; the indicator turns and lights red.
/// - Album artwork spins inside the recessed front aperture with parallax.
class LyroSpeakerPlayer extends StatefulWidget {
  final Song song;
  final bool isPlaying;
  final bool isShuffle;
  final VoidCallback onPlayPause;
  final VoidCallback onToggleShuffle;
  final double width;

  /// Box height; defaults to `width * 1.3`. The model is scaled to fit.
  final double? height;

  const LyroSpeakerPlayer({
    super.key,
    required this.song,
    required this.isPlaying,
    required this.isShuffle,
    required this.onPlayPause,
    required this.onToggleShuffle,
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

  // Knob press, knob play-position and shuffle dial
  late final AnimationController _knobPressController;
  late final Animation<double> _knobPressAnimation;
  late final AnimationController _knobTurnController;
  late final AnimationController _dialRotationController;

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
    if (widget.isPlaying) _artworkRotationController.repeat();

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
      value: widget.isShuffle ? 1.0 : 0.0,
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
    if (widget.isPlaying) {
      _waveLevelController.value = 1.0;
      _wavePhaseController.repeat();
    }
  }

  @override
  void didUpdateWidget(covariant LyroSpeakerPlayer oldWidget) {
    super.didUpdateWidget(oldWidget);

    if (widget.isPlaying != oldWidget.isPlaying) {
      if (widget.isPlaying) {
        if (!_artworkRotationController.isAnimating) {
          _artworkRotationController.repeat();
        }
        _knobTurnController.animateTo(1.0, curve: Curves.easeOutBack);
        if (!_wavePhaseController.isAnimating) _wavePhaseController.repeat();
        _waveLevelController.forward();
      } else {
        _artworkRotationController.stop(canceled: false);
        _knobTurnController.animateTo(0.0, curve: Curves.easeOutBack);
        _waveLevelController.reverse();
      }
    }

    if (widget.isShuffle != oldWidget.isShuffle) {
      widget.isShuffle
          ? _dialRotationController.forward()
          : _dialRotationController.reverse();
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
    final camera = SpeakerCamera(
      yaw: _yawController.value,
      pitch: _pitchController.value,
      // Fits the model plus room for the sound waves on either side.
      scale: math.min(w / 1.75, h / 1.95),
      center: Offset(w / 2, h * 0.5),
    );
    final pose = SpeakerPose(
      camera: camera,
      knobPress: _knobPressAnimation.value,
      knobTurn: _knobTurnController.value,
      dialTurn: _dialRotationController.value,
      shuffle: widget.isShuffle,
    );

    final knobCenter = camera.screen(pose.knobTop);
    final knobSize = camera.scaleAt(pose.knobTop) * 0.3;
    final dialCenter = camera.screen(pose.dialTop);
    final dialSize = camera.scaleAt(pose.dialTop) * 0.22;
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
        );

    return Stack(
      clipBehavior: Clip.none,
      children: [
        Positioned.fill(
          child: IgnorePointer(child: CustomPaint(painter: waves(near: false))),
        ),
        Positioned.fill(child: CustomPaint(painter: SpeakerBackPainter(pose))),
        Positioned.fill(
          child: IgnorePointer(child: CustomPaint(painter: waves(near: true))),
        ),
        if (SpeakerFrontFace.visibility(camera) > 0.02)
          Positioned(
            left: 0,
            top: 0,
            child: IgnorePointer(
              child: SpeakerFrontFace(
                camera: camera,
                artwork: (diameter) => RotationTransition(
                  turns: _artworkRotationController,
                  child: TrackArtwork(
                    key: ValueKey('speaker_art_${widget.song.id}'),
                    songId: widget.song.id,
                    title: widget.song.title,
                    size: diameter,
                    radius: diameter / 2,
                  ),
                ),
                grille: CustomPaint(
                  painter: SpeakerGrillePainter(isDark: true, opacity: 0.22),
                ),
              ),
            ),
          ),
        Positioned.fill(
          child: IgnorePointer(
            child: CustomPaint(painter: SpeakerFrontPainter(pose)),
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
            child: GestureDetector(
              key: const Key('speaker_shuffle_dial'),
              behavior: HitTestBehavior.opaque,
              onTap: () {
                HapticFeedback.lightImpact();
                widget.onToggleShuffle();
              },
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
      ],
    );
  }
}
