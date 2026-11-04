import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'cmf_speaker_3d.dart';

/// Still, non-interactive render of a speaker model at the resting view.
class SpeakerPreview extends StatelessWidget {
  final SpeakerShape shape;
  final Color body;
  final Color knob;
  final Color dial;
  final Color logoColor;
  final double width;
  final double height;

  const SpeakerPreview({
    super.key,
    required this.shape,
    required this.body,
    required this.knob,
    required this.dial,
    required this.logoColor,
    required this.width,
    required this.height,
  });

  @override
  Widget build(BuildContext context) {
    final camera = SpeakerCamera(
      yaw: 0.5,
      pitch: 0.3,
      scale: math.min(width / shape.fitWidth, height / shape.fitHeight),
      center: Offset(width / 2, height * 0.5),
      pivotY: shape.pivotY,
    );
    final pose = SpeakerPose(
      camera: camera,
      knobPress: 0,
      knobTurn: 0,
      dialTurn: 0,
      dialLit: false,
      body: body,
      knob: knob,
      dial: dial,
      shape: shape,
    );

    return IgnorePointer(
      child: RepaintBoundary(
        child: SizedBox(
          width: width,
          height: height,
          child: Stack(
            clipBehavior: Clip.none,
            children: [
              Positioned.fill(child: CustomPaint(painter: SpeakerBackPainter(pose))),
              Positioned(
                left: 0,
                top: 0,
                child: SpeakerFrontFace(
                  camera: camera,
                  artwork: (diameter) => const SizedBox.expand(),
                  grille: const SizedBox.shrink(),
                  logoColor: logoColor,
                  shape: shape,
                ),
              ),
              Positioned.fill(child: CustomPaint(painter: SpeakerFrontPainter(pose))),
            ],
          ),
        ),
      ),
    );
  }
}
