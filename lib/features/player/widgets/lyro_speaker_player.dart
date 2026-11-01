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
/// - Drag to orbit the speaker a full 360°; a fling keeps spinning and springs
///   back to the resting three-quarter view.
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
  static const double _restYaw = 0.5;
  static const double _restPitch = 0.3;
  static const _spring = SpringDescription(mass: 1, stiffness: 38, damping: 11);

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
    _pitchController =
        AnimationController.unbounded(vsync: this, value: _restPitch);

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
      } else {
        _artworkRotationController.stop(canceled: false);
        _knobTurnController.animateTo(0.0, curve: Curves.easeOutBack);
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
    super.dispose();
  }

  void _onPanStart(DragStartDetails details) {
    _yawController.stop();
    _pitchController.stop();
  }

  void _onPanUpdate(DragUpdateDetails details) {
    final k = 2.6 / widget.width;
    _yawController.value += details.delta.dx * k;
    _pitchController.value =
        (_pitchController.value + details.delta.dy * k * 0.5).clamp(0.02, 0.62);
  }

  void _onPanEnd(DragEndDetails details) {
    final k = 2.6 / widget.width;
    final velocity = details.velocity.pixelsPerSecond.dx * k;
    final yaw = _yawController.value;
    final twoPi = math.pi * 2;

    // Let the fling carry the spin, then settle on the nearest resting view.
    final projected = yaw + velocity * 0.35;
    final target =
        _restYaw + ((projected - _restYaw) / twoPi).round() * twoPi;
    _yawController.animateWith(SpringSimulation(_spring, yaw, target, velocity));
    _pitchController.animateWith(
        SpringSimulation(_spring, _pitchController.value, _restPitch, 0));
  }

  @override
  Widget build(BuildContext context) {
    final w = widget.width;
    final h = w * 1.3;

    return Center(
      child: GestureDetector(
        behavior: HitTestBehavior.translucent,
        onPanStart: _onPanStart,
        onPanUpdate: _onPanUpdate,
        onPanEnd: _onPanEnd,
        onPanCancel: () => _onPanEnd(DragEndDetails()),
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
    );
  }

  Widget _buildScene(double w, double h) {
    final camera = SpeakerCamera(
      yaw: _yawController.value,
      pitch: _pitchController.value,
      scale: w * 0.62,
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

    return Stack(
      clipBehavior: Clip.none,
      children: [
        Positioned.fill(
          child: CustomPaint(painter: SpeakerBackPainter(pose)),
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
