import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

/// Real-time procedural 3D speaker models, rendered with Canvas.
///
/// World space: x → right, y → up, z → towards the front face. Every model is
/// a convex profile extruded with rounded edges, carrying a play knob and a
/// sleep dial on its control panel. Speakers extrude a side profile along x;
/// slim devices extrude their front outline along z. [SpeakerShape] holds what
/// differs between models.

// =============================================================================
// MATH
// =============================================================================

class V3 {
  final double x, y, z;
  const V3(this.x, this.y, this.z);

  V3 operator +(V3 o) => V3(x + o.x, y + o.y, z + o.z);
  V3 operator -(V3 o) => V3(x - o.x, y - o.y, z - o.z);
  V3 operator *(double s) => V3(x * s, y * s, z * s);
  double dot(V3 o) => x * o.x + y * o.y + z * o.z;
  V3 cross(V3 o) => V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
  double get length => math.sqrt(x * x + y * y + z * z);
  V3 get normalized {
    final l = length;
    return l == 0 ? this : V3(x / l, y / l, z / l);
  }

  static const xAxis = V3(1, 0, 0);
  static const yAxis = V3(0, 1, 0);
  static const zAxis = V3(0, 0, 1);
}

/// Orbit camera: the model is yawed about Y, then pitched about X around
/// [pivotY], and viewed from [distance] units with a perspective projection.
class SpeakerCamera {
  final double yaw;
  final double pitch;
  final double scale; // screen px per world unit at the pivot depth
  final Offset center; // screen position of the pivot
  final double distance;
  final double pivotY;

  late final double _cy = math.cos(yaw), _sy = math.sin(yaw);
  late final double _cp = math.cos(pitch), _sp = math.sin(pitch);

  SpeakerCamera({
    required this.yaw,
    required this.pitch,
    required this.scale,
    required this.center,
    this.distance = 5.0,
    this.pivotY = 0.74,
  });

  V3 rotate(V3 p) {
    final x1 = p.x * _cy + p.z * _sy;
    final z1 = -p.x * _sy + p.z * _cy;
    return V3(x1, p.y * _cp - z1 * _sp, p.y * _sp + z1 * _cp);
  }

  V3 toView(V3 p) {
    final r = rotate(V3(p.x, p.y - pivotY, p.z));
    return V3(r.x, r.y, r.z - distance);
  }

  Offset project(V3 v) {
    final k = distance / -v.z;
    return Offset(center.dx + scale * k * v.x, center.dy - scale * k * v.y);
  }

  Offset screen(V3 world) => project(toView(world));

  /// Screen px per world unit at a given world point.
  double scaleAt(V3 world) => scale * distance / -toView(world).z;

  /// How directly a surface faces the camera (> 0 means front-facing).
  double facing(V3 point, V3 normal) {
    final v = (toView(point) * -1).normalized;
    return rotate(normal).dot(v);
  }

  /// Matrix mapping a flat widget's local (u, v) pixels onto the plane
  /// `origin + u * uStep + v * vStep` in world space, then to the screen.
  Matrix4 planeMatrix(V3 origin, V3 uStep, V3 vStep) {
    final o = toView(origin), u = rotate(uStep), v = rotate(vStep);
    final s = scale, d = distance, cx = center.dx, cy = center.dy;
    return Matrix4.zero()
      ..setEntry(0, 0, s * u.x - cx * u.z / d)
      ..setEntry(0, 1, s * v.x - cx * v.z / d)
      ..setEntry(0, 3, s * o.x - cx * o.z / d)
      ..setEntry(1, 0, -s * u.y - cy * u.z / d)
      ..setEntry(1, 1, -s * v.y - cy * v.z / d)
      ..setEntry(1, 3, -s * o.y - cy * o.z / d)
      ..setEntry(2, 2, 1)
      ..setEntry(3, 0, -u.z / d)
      ..setEntry(3, 1, -v.z / d)
      ..setEntry(3, 3, -o.z / d);
  }
}

// =============================================================================
// LIGHTING (studio rig fixed to the camera, like the turntable render)
// =============================================================================

final V3 _keyLight = const V3(-0.55, 0.75, 0.55).normalized;
final V3 _fillLight = const V3(0.85, -0.05, 0.35).normalized;

Color shadeColor(
  Color base,
  V3 nView,
  V3 pView, {
  double spec = 0.16,
  double gloss = 24,
  double rim = 0.12,
}) {
  final n = nView.normalized;
  final v = (pView * -1).normalized;
  final d1 = math.max(0.0, n.dot(_keyLight));
  final d2 = math.max(0.0, n.dot(_fillLight));
  final h = (_keyLight + v).normalized;
  final sp = math.pow(math.max(0.0, n.dot(h)), gloss) * spec;
  final fr = math.pow(1 - n.dot(v).clamp(0.0, 1.0), 3) * rim;
  final k = 0.30 + 0.72 * d1 + 0.20 * d2;
  final add = (sp + fr) * 255;
  int ch(double c) => (c * 255 * k + add).round().clamp(0, 255);
  return Color.fromARGB(255, ch(base.r), ch(base.g), ch(base.b));
}

// =============================================================================
// MESHES
// =============================================================================

class _Mesh {
  final List<V3> positions = [];
  final List<V3> normals = [];
  final List<int> indices = [];
  final Color color;
  final double spec, gloss;

  _Mesh(this.color, {this.spec = 0.16, this.gloss = 24});

  int vertex(V3 p, V3 n) {
    positions.add(p);
    normals.add(n);
    return positions.length - 1;
  }

  /// Adds a triangle wound so that its face normal agrees with [outward].
  void tri(int a, int b, int c, V3 outward) {
    final n = (positions[b] - positions[a]).cross(positions[c] - positions[a]);
    if (n.dot(outward) >= 0) {
      indices.addAll([a, b, c]);
    } else {
      indices.addAll([a, c, b]);
    }
  }

  void quad(int a, int b, int c, int d, V3 outward) {
    tri(a, b, c, outward);
    tri(a, c, d, outward);
  }

  /// Outward direction for convex parts: away from [center].
  V3 away(V3 center, List<int> ids) {
    var c = const V3(0, 0, 0);
    for (final i in ids) {
      c = c + positions[i];
    }
    return c * (1 / ids.length) - center;
  }

  void draw(Canvas canvas, SpeakerCamera cam, {Color? tint}) {
    final color = tint ?? this.color;
    final n = positions.length;
    final view = List<V3>.generate(n, (i) => cam.toView(positions[i]));
    final scr = List<Offset>.generate(n, (i) => cam.project(view[i]));
    final cols = List<int>.filled(n, 0);
    final lit = List<bool>.filled(n, false);

    final pos = <double>[];
    final col = <int>[];
    for (var t = 0; t < indices.length; t += 3) {
      final a = indices[t], b = indices[t + 1], c = indices[t + 2];
      final fn = (view[b] - view[a]).cross(view[c] - view[a]);
      if (fn.dot(view[a] * -1) <= 0) continue; // back-face cull
      for (final i in [a, b, c]) {
        if (!lit[i]) {
          cols[i] = shadeColor(color, cam.rotate(normals[i]), view[i],
                  spec: spec, gloss: gloss)
              .toARGB32();
          lit[i] = true;
        }
        pos..add(scr[i].dx)..add(scr[i].dy);
        col.add(cols[i]);
      }
    }
    if (pos.isEmpty) return;
    canvas.drawVertices(
      ui.Vertices.raw(
        VertexMode.triangles,
        Float32List.fromList(pos),
        colors: Int32List.fromList(col),
      ),
      BlendMode.dst,
      Paint(),
    );
  }
}

/// A point on a rounded 2D profile: arc [center] and unit [normal].
class _ProfilePoint {
  final Offset center; // (z, y)
  final Offset normal;
  const _ProfilePoint(this.center, this.normal);
}

/// Rounds a convex CCW polygon in the (z, y) plane with corner radius [r].
List<_ProfilePoint> _roundedProfile(List<Offset> poly, double r, int seg) {
  Offset edgeNormal(Offset a, Offset b) {
    final d = b - a;
    return Offset(d.dy, -d.dx) / d.distance;
  }

  final out = <_ProfilePoint>[];
  final m = poly.length;
  for (var i = 0; i < m; i++) {
    final p = poly[i];
    final n1 = edgeNormal(poly[(i - 1 + m) % m], p);
    final n2 = edgeNormal(p, poly[(i + 1) % m]);
    final dotN = n1.dx * n2.dx + n1.dy * n2.dy;
    final c = p - (n1 + n2) * (r / (1 + dotN));
    final a1 = math.atan2(n1.dy, n1.dx);
    var a2 = math.atan2(n2.dy, n2.dx);
    if (a2 < a1) a2 += math.pi * 2;
    for (var j = 0; j <= seg; j++) {
      final a = a1 + (a2 - a1) * j / seg;
      out.add(_ProfilePoint(c, Offset(math.cos(a), math.sin(a))));
    }
  }
  return out;
}

/// Low cylinder (with a chamfered top edge) standing on [origin] along [axis].
_Mesh _cylinder({
  required V3 origin,
  required V3 axis,
  required V3 radial,
  required double radius,
  required double height,
  required double chamfer,
  required Color color,
  double spec = 0.2,
  double gloss = 30,
  int segments = 28,
}) {
  final mesh = _Mesh(color, spec: spec, gloss: gloss);
  final r2 = axis.cross(radial).normalized;
  V3 dir(int i) {
    final a = i * math.pi * 2 / segments;
    return radial * math.cos(a) + r2 * math.sin(a);
  }

  final rings = [
    (radius, 0.0, 1.0),
    (radius, height - chamfer, 1.0),
    (radius - chamfer, height, 0.35),
  ];
  final ids = <List<int>>[];
  for (final (rad, h, w) in rings) {
    ids.add([
      for (var i = 0; i < segments; i++)
        mesh.vertex(origin + dir(i) * rad + axis * h,
            (dir(i) * w + axis * (1 - w)).normalized),
    ]);
  }
  for (var k = 0; k < rings.length - 1; k++) {
    for (var i = 0; i < segments; i++) {
      final j = (i + 1) % segments;
      final q = [ids[k][i], ids[k][j], ids[k + 1][j], ids[k + 1][i]];
      mesh.quad(q[0], q[1], q[2], q[3], dir(i) + dir(j));
    }
  }
  final top = origin + axis * height;
  final c = mesh.vertex(top, axis);
  final capIds = [
    for (var i = 0; i < segments; i++)
      mesh.vertex(origin + dir(i) * (radius - chamfer) + axis * height, axis),
  ];
  for (var i = 0; i < segments; i++) {
    mesh.tri(c, capIds[i], capIds[(i + 1) % segments], axis);
  }
  return mesh;
}

/// Box with flat faces centred at [center] with half extents along a/b/c.
_Mesh _box(V3 center, V3 a, V3 b, V3 c, Color color,
    {double spec = 0.2, double gloss = 30}) {
  final mesh = _Mesh(color, spec: spec, gloss: gloss);
  for (final (n, u, v) in [(a, b, c), (b, c, a), (c, a, b)]) {
    for (final s in [1.0, -1.0]) {
      final fc = center + n * s;
      final nn = n.normalized * s;
      final q = [
        mesh.vertex(fc + u + v, nn),
        mesh.vertex(fc - u + v, nn),
        mesh.vertex(fc - u - v, nn),
        mesh.vertex(fc + u - v, nn),
      ];
      mesh.quad(q[0], q[1], q[2], q[3], nn);
    }
  }
  return mesh;
}

// =============================================================================
// GEOMETRY
// =============================================================================

typedef _G = SpeakerGeometry;

/// Front-face detail that sets a model apart.
enum SpeakerDetail { none, slats, screws, woofer, wheel, cassette, twinWoofers }

/// One hardware design: its profile, fittings and how it sits in the frame.
class SpeakerShape {
  final String label;

  // Body. [profile] is a convex CCW polygon: a side view in (z, y), or with
  // [frontOutline] the front view in (x, y). [wallHeight] is the height of
  // the flat front wall.
  final double width, wallHeight, depth;
  final List<Offset> profile;
  final bool frontOutline;
  final double cornerRadius, bevel;

  /// Moves the front wall forward of the default depth / 2 (Headphones).
  final double? faceOffset;

  /// The panel carrying the controls, front to back in (z, y). A vertical
  /// line puts the controls on the front wall.
  final Offset roofFront, roofBack;
  final bool backRoofButton;

  // Plate or handle standing on the top (none when [finHeight] is 0).
  final double finHeight, finThickness, finHalfWidth, finCorner, finZ;
  final double holeX, holeRise, holeWidth, holeHeight;

  final bool antenna;
  final bool wrappedEarphones;
  final bool headband;

  // Front-face speaker aperture; [screen] shows still art in a flat display.
  final double apertureX, apertureY, apertureRadius;
  final bool screen;
  final double logoX, logoY; // from the top-left of the front wall
  final SpeakerDetail detail;

  final double knobX, dialX, knobScale, dialScale;

  /// A flat push button rather than the bar knob.
  final bool buttonKnob;

  // Side passive radiators, and back ports (none when [sideHeight] is 0).
  final double sideWidth, sideHeight, sideY;

  // Framing: orbit pivot, and the world size that must fit the widget.
  final double pivotY, fitWidth, fitHeight;

  const SpeakerShape({
    required this.label,
    required this.width,
    required this.wallHeight,
    required this.depth,
    required this.profile,
    this.frontOutline = false,
    required this.cornerRadius,
    required this.bevel,
    this.faceOffset,
    required this.roofFront,
    required this.roofBack,
    this.backRoofButton = false,
    this.finHeight = 0,
    this.finThickness = 0,
    this.finHalfWidth = 0,
    this.finCorner = 0,
    this.finZ = 0,
    this.holeX = 0,
    this.holeRise = 0,
    this.holeWidth = 0,
    this.holeHeight = 0,
    this.antenna = false,
    this.wrappedEarphones = false,
    this.headband = false,
    required this.apertureX,
    required this.apertureY,
    required this.apertureRadius,
    this.screen = false,
    this.logoX = 0.08,
    this.logoY = 0.05,
    this.detail = SpeakerDetail.none,
    required this.knobX,
    required this.dialX,
    this.knobScale = 1,
    this.dialScale = 1,
    this.buttonKnob = false,
    this.sideWidth = 0,
    this.sideHeight = 0,
    this.sideY = 0,
    required this.pivotY,
    required this.fitWidth,
    required this.fitHeight,
  });

  /// House-shaped prism with a lanyard plate on the ridge.
  static const classic = SpeakerShape(
    label: 'Classic',
    width: 1.0,
    wallHeight: 0.98,
    depth: 0.74,
    profile: [Offset(-0.37, 0), Offset(0.37, 0), Offset(0.37, 0.98), Offset(0, 1.21), Offset(-0.37, 0.98)],
    cornerRadius: 0.075,
    bevel: 0.05,
    roofFront: Offset(0.37, 0.98),
    roofBack: Offset(0, 1.21),
    backRoofButton: true,
    finHeight: 0.36,
    finThickness: 0.055,
    finHalfWidth: 0.43,
    finCorner: 0.09,
    holeX: 0.27,
    holeRise: 0.2,
    holeWidth: 0.17,
    holeHeight: 0.17,
    apertureX: 0.02,
    apertureY: 0.47,
    apertureRadius: 0.36,
    knobX: 0.27,
    dialX: -0.28,
    sideWidth: 0.27,
    sideHeight: 0.6,
    sideY: 0.46,
    pivotY: 0.74,
    fitWidth: 1.75,
    fitHeight: 1.95,
  );

  /// Wide, shallow transistor radio with an antenna and side slats.
  static const radio = SpeakerShape(
    label: 'Radio',
    width: 1.36,
    wallHeight: 0.84,
    depth: 0.44,
    profile: [Offset(-0.22, 0), Offset(0.22, 0), Offset(0.22, 0.84), Offset(-0.22, 0.84)],
    cornerRadius: 0.06,
    bevel: 0.04,
    roofFront: Offset(0.22, 0.84),
    roofBack: Offset(-0.22, 0.84),
    antenna: true,
    apertureX: -0.29,
    apertureY: 0.4,
    apertureRadius: 0.32,
    logoX: 0.82,
    logoY: 0.08,
    detail: SpeakerDetail.slats,
    knobX: 0.44,
    dialX: 0.14,
    sideWidth: 0.2,
    sideHeight: 0.5,
    sideY: 0.4,
    pivotY: 0.62,
    fitWidth: 2.1,
    fitHeight: 1.9,
  );

  /// Chunky soft cube with a carry handle.
  static const cube = SpeakerShape(
    label: 'Cube',
    width: 1.02,
    wallHeight: 1.0,
    depth: 0.88,
    profile: [Offset(-0.44, 0), Offset(0.44, 0), Offset(0.44, 1.0), Offset(-0.44, 1.0)],
    cornerRadius: 0.15,
    bevel: 0.1,
    roofFront: Offset(0.44, 1.0),
    roofBack: Offset(-0.1, 1.0),
    finHeight: 0.3,
    finThickness: 0.075,
    finHalfWidth: 0.37,
    finCorner: 0.13,
    finZ: -0.24,
    holeRise: 0.165,
    holeWidth: 0.5,
    holeHeight: 0.13,
    apertureX: 0,
    apertureY: 0.5,
    apertureRadius: 0.38,
    logoX: 0.39,
    logoY: 0.025,
    detail: SpeakerDetail.screws,
    knobX: 0.26,
    dialX: -0.26,
    sideWidth: 0.34,
    sideHeight: 0.58,
    sideY: 0.5,
    pivotY: 0.66,
    fitWidth: 1.85,
    fitHeight: 1.85,
  );

  /// Tall column with a sloped control panel, artwork over a woofer.
  static const tower = SpeakerShape(
    label: 'Tower',
    width: 0.76,
    wallHeight: 1.24,
    depth: 0.62,
    profile: [Offset(-0.31, 0), Offset(0.31, 0), Offset(0.31, 1.24), Offset(-0.31, 1.5)],
    cornerRadius: 0.06,
    bevel: 0.045,
    roofFront: Offset(0.31, 1.24),
    roofBack: Offset(-0.31, 1.5),
    apertureX: 0,
    apertureY: 0.9,
    apertureRadius: 0.3,
    logoX: 0.285,
    logoY: 0.655,
    detail: SpeakerDetail.woofer,
    knobX: 0.17,
    dialX: -0.17,
    sideWidth: 0.26,
    sideHeight: 0.9,
    sideY: 0.66,
    pivotY: 0.78,
    fitWidth: 1.6,
    fitHeight: 1.95,
  );

  static const _pocketOutline = [
    Offset(-0.37, 0),
    Offset(0.37, 0),
    Offset(0.37, 1.24),
    Offset(-0.37, 1.24),
  ];

  /// Slim pocket player with a display over a round control wheel.
  static const pocket = SpeakerShape(
    label: 'Pocket',
    width: 0.74,
    wallHeight: 1.24,
    depth: 0.13,
    profile: _pocketOutline,
    frontOutline: true,
    cornerRadius: 0.09,
    bevel: 0.03,
    roofFront: Offset(0.065, 0.25),
    roofBack: Offset(0.065, 0.35),
    apertureX: 0,
    apertureY: 0.9,
    apertureRadius: 0.27,
    screen: true,
    logoX: 0.265,
    logoY: 0.615,
    detail: SpeakerDetail.wheel,
    knobX: 0,
    dialX: 0.165,
    knobScale: 0.7,
    dialScale: 0.55,
    buttonKnob: true,
    pivotY: 0.62,
    fitWidth: 1.2,
    fitHeight: 1.7,
  );

  /// The pocket player with earphones wound around it.
  static const wrapped = SpeakerShape(
    label: 'Wrapped',
    width: 0.74,
    wallHeight: 1.24,
    depth: 0.13,
    profile: _pocketOutline,
    frontOutline: true,
    cornerRadius: 0.09,
    bevel: 0.03,
    wrappedEarphones: true,
    roofFront: Offset(0.065, 0.25),
    roofBack: Offset(0.065, 0.35),
    apertureX: 0,
    apertureY: 0.9,
    apertureRadius: 0.27,
    screen: true,
    logoX: 0.265,
    logoY: 0.615,
    detail: SpeakerDetail.wheel,
    knobX: 0,
    dialX: 0.165,
    knobScale: 0.7,
    dialScale: 0.55,
    buttonKnob: true,
    pivotY: 0.62,
    fitWidth: 1.6,
    fitHeight: 1.7,
  );

  /// Portable cassette player: art turns on one reel behind the window.
  static const cassette = SpeakerShape(
    label: 'Cassette',
    width: 1.22,
    wallHeight: 0.84,
    depth: 0.28,
    profile: [Offset(-0.61, 0), Offset(0.61, 0), Offset(0.61, 0.84), Offset(-0.61, 0.84)],
    frontOutline: true,
    cornerRadius: 0.06,
    bevel: 0.03,
    roofFront: Offset(0.1, 0.84),
    roofBack: Offset(-0.1, 0.84),
    apertureX: -0.25,
    apertureY: 0.42,
    apertureRadius: 0.2,
    detail: SpeakerDetail.cassette,
    knobX: 0.42,
    dialX: 0.14,
    knobScale: 0.75,
    dialScale: 0.8,
    pivotY: 0.5,
    fitWidth: 1.85,
    fitHeight: 1.45,
  );

  /// Over-ear headphones standing up, one cup facing front.
  static const headphones = SpeakerShape(
    label: 'Headphones',
    width: 0.82,
    wallHeight: 1.1,
    depth: 0.26,
    profile: [Offset(-0.41, 0), Offset(0.41, 0), Offset(0.41, 1.1), Offset(-0.41, 1.1)],
    frontOutline: true,
    cornerRadius: 0.3,
    bevel: 0.05,
    faceOffset: 0.46,
    headband: true,
    roofFront: Offset(0.46, 0.16),
    roofBack: Offset(0.46, 0.26),
    apertureX: 0,
    apertureY: 0.66,
    apertureRadius: 0.28,
    logoX: 0.3,
    logoY: 0.06,
    knobX: 0.14,
    dialX: -0.14,
    knobScale: 0.72,
    dialScale: 0.72,
    buttonKnob: true,
    pivotY: 0.74,
    fitWidth: 1.6,
    fitHeight: 1.95,
  );

  /// Wide stereo with twin woofers, artwork between them and a long handle.
  static const boombox = SpeakerShape(
    label: 'Boombox',
    width: 1.66,
    wallHeight: 0.86,
    depth: 0.46,
    profile: [Offset(-0.23, 0), Offset(0.23, 0), Offset(0.23, 0.86), Offset(-0.23, 0.86)],
    cornerRadius: 0.07,
    bevel: 0.045,
    roofFront: Offset(0.23, 0.86),
    roofBack: Offset(-0.03, 0.86),
    finHeight: 0.24,
    finThickness: 0.05,
    finHalfWidth: 0.62,
    finCorner: 0.1,
    finZ: -0.13,
    holeRise: 0.13,
    holeWidth: 1.06,
    holeHeight: 0.1,
    apertureX: 0,
    apertureY: 0.4,
    apertureRadius: 0.25,
    logoX: 0.72,
    logoY: 0.06,
    detail: SpeakerDetail.twinWoofers,
    knobX: 0.42,
    dialX: -0.42,
    knobScale: 0.8,
    sideWidth: 0.2,
    sideHeight: 0.5,
    sideY: 0.42,
    pivotY: 0.56,
    fitWidth: 2.45,
    fitHeight: 1.8,
  );

  static const List<SpeakerShape> values = [
    classic,
    radio,
    cube,
    tower,
    pocket,
    wrapped,
    cassette,
    headphones,
    boombox,
  ];

  /// Z of the flat front wall.
  double get faceZ => faceOffset ?? depth / 2;
}

/// The geometry of the model being built or painted. As with [_paintBody],
/// every painter and builder sets [active] first, so several models can
/// be on screen at once.
class SpeakerGeometry {
  static SpeakerShape active = SpeakerShape.classic;

  static double get width => active.width;
  static double get wallHeight => active.wallHeight;
  static double get depth => active.depth;
  static double get faceZ => active.faceZ;
  static double get cornerRadius => active.cornerRadius;
  static double get bevel => active.bevel;

  static double get finHeight => active.finHeight;
  static double get finThickness => active.finThickness;
  static double get finHalfWidth => active.finHalfWidth;
  static double get finCorner => active.finCorner;
  static double get finZ => active.finZ;

  // Front-face speaker aperture
  static double get apertureX => active.apertureX;
  static double get apertureY => active.apertureY;
  static double get apertureRadius => active.apertureRadius;
  static const double apertureDepth = 0.05;

  static double get knobX => active.knobX;
  static double get dialX => active.dialX;

  static const Color bodyColor = Color(0xFF3B3D44);
  static const Color knobColor = Color(0xFFE3262D);
  static const Color dialColor = Color(0xFF26272C);

  static V3 get roofUp {
    final d = active.roofBack - active.roofFront;
    return V3(0, d.dy, d.dx).normalized;
  }

  static V3 get roofNormal {
    final u = roofUp;
    return V3(0, -u.z, u.y);
  }

  static V3 get roofMid {
    final m = (active.roofFront + active.roofBack) / 2;
    return V3(0, m.dy, m.dx);
  }

  static final V3 backRoofUp = const V3(0, 0.23, 0.37).normalized;
  static final V3 backRoofNormal = const V3(0, 0.37, -0.23).normalized;

  static List<Offset> get profile => active.profile;

  static final Map<SpeakerShape, double> _ridgeTops = {};

  /// Highest point of the rounded profile, where a fin is mounted.
  static double get ridgeTop => _ridgeTops[active] ??= () {
        final pts = _roundedProfile(profile, cornerRadius, 1);
        return pts.map((p) => p.center.dy + cornerRadius * p.normal.dy).reduce(math.max);
      }();

  static V3 knobBase(double x) => V3(x, roofMid.y, roofMid.z);
}

/// Rounded-edge body: profile rings swept along x with a quarter-round bevel
/// into the flat side caps.
final Map<SpeakerShape, _Mesh> _bodyMeshes = {};
_Mesh get _bodyMesh => _bodyMeshes[_G.active] ??= _G.active.frontOutline
    ? _frontExtrusion(_G.profile, _G.cornerRadius, _G.bevel, _G.depth, _G.faceZ - _G.depth / 2)
    : _extrude(_G.profile, _G.cornerRadius, _G.bevel, _G.width, (v) => v, (v) => v);

/// Front outline in (x, y) extruded along z, centred on [z].
_Mesh _frontExtrusion(List<Offset> poly, double corner, double bevel, double length, double z,
    {Color color = _G.bodyColor}) {
  return _extrude(poly, corner, bevel, length, (v) => V3(v.z, v.y, v.x + z), (v) => V3(v.z, v.y, v.x),
      color: color);
}

/// Sweeps [poly] (u, y) along a line of [length]. In the local frame the
/// sweep is x and u is z; [pos] and [dir] place points and normals.
_Mesh _extrude(List<Offset> poly, double corner, double bevel, double length,
    V3 Function(V3) pos, V3 Function(V3) dir,
    {Color color = _G.bodyColor}) {
  final mesh = _Mesh(color, spec: 0.14, gloss: 18);
  final prof = _roundedProfile(poly, corner, corner > 0.2 ? 12 : 7);
  const steps = 3;
  final midY = poly.map((p) => p.dy).reduce(math.max) * 0.55;
  final center = pos(V3(0, midY, 0));

  final rings = <List<int>>[];
  final ringSpec = <(double, double)>[
    for (var k = steps; k >= 0; k--) (-1.0, k * math.pi / 2 / steps),
    for (var k = 0; k <= steps; k++) (1.0, k * math.pi / 2 / steps),
  ];
  for (final (sgn, th) in ringSpec) {
    final x = sgn * (length / 2 - bevel + bevel * math.sin(th));
    final rr = corner - bevel * (1 - math.cos(th));
    rings.add([
      for (final p in prof)
        mesh.vertex(
          pos(V3(x, p.center.dy + p.normal.dy * rr, p.center.dx + p.normal.dx * rr)),
          dir(V3(sgn * math.sin(th), math.cos(th) * p.normal.dy,
              math.cos(th) * p.normal.dx)),
        ),
    ]);
  }
  final m = prof.length;
  for (var k = 0; k < rings.length - 1; k++) {
    for (var i = 0; i < m; i++) {
      final j = (i + 1) % m;
      final q = [rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]];
      final p0 = mesh.positions[q[0]], p1 = mesh.positions[q[2]];
      if ((p0 - p1).length < 1e-6) continue;
      mesh.quad(q[0], q[1], q[2], q[3], mesh.away(center, q));
    }
  }
  // Flat end caps
  for (final sgn in [-1.0, 1.0]) {
    final ring = sgn < 0 ? rings.first : rings.last;
    final n = dir(V3(sgn, 0, 0));
    final ids = [for (final i in ring) mesh.vertex(mesh.positions[i], n)];
    final c = mesh.vertex(pos(V3(sgn * length / 2, midY, 0)), n);
    for (var i = 0; i < m; i++) {
      mesh.tri(c, ids[i], ids[(i + 1) % m], n);
    }
  }
  return mesh;
}

/// Round tube along [points]; [up] must never run along the tube.
_Mesh _tube(List<V3> points, double radius, Color color,
    {double spec = 0.3, double gloss = 30, V3 up = V3.zAxis}) {
  const sides = 6;
  final mesh = _Mesh(color, spec: spec, gloss: gloss);
  final rings = <List<int>>[];
  final dirs = <List<V3>>[];
  for (var i = 0; i < points.length; i++) {
    final a = points[math.max(0, i - 1)], b = points[math.min(points.length - 1, i + 1)];
    final t = (b - a).normalized;
    final u = t.cross(up).normalized, v = t.cross(u).normalized;
    final ring = <int>[];
    final ds = <V3>[];
    for (var k = 0; k < sides; k++) {
      final an = k * 2 * math.pi / sides;
      final d = u * math.cos(an) + v * math.sin(an);
      ds.add(d);
      ring.add(mesh.vertex(points[i] + d * radius, d));
    }
    rings.add(ring);
    dirs.add(ds);
  }
  for (var i = 0; i < rings.length - 1; i++) {
    for (var k = 0; k < sides; k++) {
      final l = (k + 1) % sides;
      mesh.quad(rings[i][k], rings[i][l], rings[i + 1][l], rings[i + 1][k], dirs[i][k] + dirs[i][l]);
    }
  }
  return mesh;
}

_Mesh _ellipsoid(V3 c, V3 r, Color color) {
  const lat = 8, lon = 12;
  final mesh = _Mesh(color, spec: 0.45, gloss: 40);
  final ids = <List<int>>[];
  for (var i = 0; i <= lat; i++) {
    final th = i * math.pi / lat;
    ids.add([
      for (var j = 0; j < lon; j++)
        () {
          final ph = j * 2 * math.pi / lon;
          final d = V3(math.sin(th) * math.cos(ph), math.cos(th), math.sin(th) * math.sin(ph));
          return mesh.vertex(c + V3(d.x * r.x, d.y * r.y, d.z * r.z), d);
        }(),
    ]);
  }
  for (var i = 0; i < lat; i++) {
    for (var j = 0; j < lon; j++) {
      final k = (j + 1) % lon;
      final q = [ids[i][j], ids[i][k], ids[i + 1][k], ids[i + 1][j]];
      mesh.quad(q[0], q[1], q[2], q[3], mesh.away(c, q));
    }
  }
  return mesh;
}

/// Parts beyond the body: drawn behind it, or in front, by which way they lie.
class _Attachments {
  final List<(_Mesh, bool)> back; // (mesh, takes the body colour)
  final List<(_Mesh, bool)> front;
  const _Attachments(this.back, this.front);
}

const _cableWhite = Color(0xFFE9E9EC);

final _Attachments _earphoneParts = () {
  const w = 0.37 + 0.016, d = 0.065 + 0.016, r = 0.03;
  final front = <(_Mesh, bool)>[];
  final back = <(_Mesh, bool)>[];
  for (var i = 0; i < 6; i++) {
    final y = 0.07 + i * 0.027;
    double tilt(double x) => y + x * (i.isEven ? 0.05 : -0.04);
    front.add((
      _tube([
        V3(-w, tilt(-w), 0),
        V3(-w, tilt(-w), d - r),
        V3(-w + r, tilt(-w), d),
        for (var x = -0.3; x < 0.33; x += 0.15) V3(x, tilt(x) + 0.006 * math.sin(x * 40 + i), d),
        V3(w - r, tilt(w), d),
        V3(w, tilt(w), d - r),
        V3(w, tilt(w), 0),
      ], 0.0125, _cableWhite, up: V3.yAxis),
      false
    ));
    back.add((
      _tube([
        V3(w, tilt(w), 0),
        V3(w, tilt(w), -d + r),
        V3(w - r, tilt(w), -d),
        V3(-w + r, tilt(-w), -d),
        V3(-w, tilt(-w), -d + r),
        V3(-w, tilt(-w), 0),
      ], 0.0125, _cableWhite, up: V3.yAxis),
      false
    ));
  }
  // Loose end up the side, splitting to the two earbuds
  const split = V3(0.5, 0.74, 0.03);
  const budA = V3(0.53, 1.06, 0.06), budB = V3(0.63, 0.9, 0.02);
  front.addAll([
    (_tube(const [V3(w, 0.2, 0), V3(0.44, 0.32, 0.02), V3(0.47, 0.54, 0.03), split], 0.0135, _cableWhite), false),
    (_tube(const [split, V3(0.51, 0.86, 0.04), V3(0.52, 0.97, 0.05)], 0.0105, _cableWhite), false),
    (_tube(const [split, V3(0.57, 0.78, 0.02), V3(0.62, 0.82, 0.02)], 0.0105, _cableWhite), false),
    (_tube(const [V3(0.52, 0.96, 0.05), budA], 0.022, _cableWhite), false),
    (_tube(const [V3(0.62, 0.81, 0.02), budB], 0.022, _cableWhite), false),
    (_ellipsoid(const V3(0.54, 1.09, 0.065), const V3(0.075, 0.06, 0.055), _cableWhite), false),
    (_ellipsoid(const V3(0.64, 0.93, 0.025), const V3(0.075, 0.06, 0.055), _cableWhite), false),
  ]);
  return _Attachments(back, front);
}();

final _Attachments _headphoneParts = () {
  final s = SpeakerShape.headphones;
  final z = s.faceZ - s.depth / 2;
  const padColor = Color(0xFF17181B);
  final inset = [for (final p in s.profile) Offset(p.dx * 0.88, 0.07 + p.dy * 0.87)];
  return _Attachments([
    (_frontExtrusion(s.profile, s.cornerRadius, s.bevel, s.depth, -z), true),
    (_frontExtrusion(inset, 0.26, 0.04, 0.1, z - 0.17, color: padColor), false),
    (_frontExtrusion(inset, 0.26, 0.04, 0.1, -z + 0.17, color: padColor), false),
    (
      _tube([
        for (var i = 0; i <= 18; i++)
          V3(0, 1.06 + 0.42 * math.sin(i * math.pi / 18), z * math.cos(i * math.pi / 18)),
      ], 0.04, _G.bodyColor, spec: 0.14, gloss: 18, up: V3.xAxis),
      true
    ),
  ], const []);
}();

_Attachments? _attachments(SpeakerShape shape) => shape.wrappedEarphones
    ? _earphoneParts
    : shape.headband
        ? _headphoneParts
        : null;

/// Fin outline in (x, y), counter-clockwise.
final Map<SpeakerShape, List<Offset>> _finOutlines = {};
List<Offset> get _finOutline => _finOutlines[_G.active] ??= () {
  final yb = SpeakerGeometry.ridgeTop - 0.004;
  final yt = SpeakerGeometry.ridgeTop + _G.finHeight;
  final hw = _G.finHalfWidth, c = _G.finCorner;
  final pts = <Offset>[Offset(-hw, yb), Offset(hw, yb)];
  void arc(Offset ctr, double a0) {
    for (var i = 0; i <= 6; i++) {
      final a = a0 + (math.pi / 2) * i / 6;
      pts.add(ctr + Offset(math.cos(a), math.sin(a)) * c);
    }
  }

  arc(Offset(hw - c, yt - c), 0);
  arc(Offset(-hw + c, yt - c), math.pi / 2);
  return pts;
}();

/// Plate hole or handle slot, counter-clockwise.
List<Offset> get _finHole {
  final c = Offset(_G.active.holeX, _G.ridgeTop + _G.active.holeRise);
  return [for (final p in _stadium(_G.active.holeWidth, _G.active.holeHeight)) p + c];
}

final Map<SpeakerShape, _Mesh> _finMeshes = {};
_Mesh get _finEdgeMesh => _finMeshes[_G.active] ??= _buildFinEdge();

_Mesh _buildFinEdge() {
  final mesh = _Mesh(_G.bodyColor, spec: 0.14, gloss: 18);
  final t = _G.finThickness / 2;
  final z = _G.finZ;
  void strip(List<Offset> loop, bool inward) {
    for (var i = 0; i < loop.length; i++) {
      final a = loop[i], b = loop[(i + 1) % loop.length];
      final d = b - a;
      var n = V3(d.dy, -d.dx, 0).normalized;
      if (inward) n = n * -1;
      final q = [
        mesh.vertex(V3(a.dx, a.dy, z + t), n),
        mesh.vertex(V3(b.dx, b.dy, z + t), n),
        mesh.vertex(V3(b.dx, b.dy, z - t), n),
        mesh.vertex(V3(a.dx, a.dy, z - t), n),
      ];
      mesh.quad(q[0], q[1], q[2], q[3], n);
    }
  }

  strip(_finOutline, false);
  strip(_finHole, true); // CCW loop; material is outside the hole
  return mesh;
}

/// Telescopic antenna, base first.
final List<_Mesh> _antennaMeshes = () {
  const steel = Color(0xFFC4C6CC);
  final axis = const V3(-0.2, 1, 0).normalized;
  const base = V3(-0.56, 0.835, -0.08);
  return [
    _cylinder(origin: base, axis: V3.yAxis, radial: V3.xAxis, radius: 0.034, height: 0.03, chamfer: 0.01, color: const Color(0xFF26272C), segments: 14),
    _cylinder(origin: base, axis: axis, radial: V3.xAxis, radius: 0.016, height: 0.34, chamfer: 0.002, color: steel, spec: 0.5, gloss: 60, segments: 10),
    _cylinder(origin: base + axis * 0.34, axis: axis, radial: V3.xAxis, radius: 0.01, height: 0.33, chamfer: 0.002, color: steel, spec: 0.5, gloss: 60, segments: 10),
    _cylinder(origin: base + axis * 0.67, axis: axis, radial: V3.xAxis, radius: 0.021, height: 0.04, chamfer: 0.012, color: steel, spec: 0.5, gloss: 60, segments: 10),
  ];
}();

// =============================================================================
// SCENE STATE
// =============================================================================

class SpeakerPose {
  final SpeakerCamera camera;
  final double knobPress; // 0..1 depression
  final double knobTurn; // 0..1 (paused → playing)
  final double dialTurn; // 0..1 (sleep timer step)
  final bool dialLit; // dial indicator in red (sleep timer active)

  /// Sleep timer remaining as 0..1 for the dot ring around the dial; null = off.
  final double? sleepFraction;

  // Colourway
  final Color body;
  final Color knob;
  final Color dial;

  final SpeakerShape shape;

  const SpeakerPose({
    required this.camera,
    required this.knobPress,
    required this.knobTurn,
    required this.dialTurn,
    required this.dialLit,
    this.sleepFraction,
    this.body = SpeakerGeometry.bodyColor,
    this.knob = SpeakerGeometry.knobColor,
    this.dial = SpeakerGeometry.dialColor,
    this.shape = SpeakerShape.classic,
  });

  bool get roofVisible =>
      camera.facing(SpeakerGeometry.roofMid, SpeakerGeometry.roofNormal) > 0;

  V3 get knobTop {
    final base = SpeakerGeometry.knobBase(SpeakerGeometry.knobX);
    final h = (shape.buttonKnob ? 0.04 : 0.12) * shape.knobScale;
    return base + SpeakerGeometry.roofNormal * (h - 0.025 * knobPress);
  }

  V3 get dialTop => SpeakerGeometry.knobBase(SpeakerGeometry.dialX) +
      SpeakerGeometry.roofNormal * (0.03 * shape.dialScale);

  List<_Mesh> _knobMeshes() {
    final axis = SpeakerGeometry.roofNormal;
    final base = SpeakerGeometry.knobBase(SpeakerGeometry.knobX) -
        axis * (0.025 * knobPress);
    final r2 = axis.cross(V3.xAxis).normalized;
    final ang = -0.55 + knobTurn * math.pi / 2 + knobPress * 0.3;
    final barDir = V3.xAxis * math.cos(ang) + r2 * math.sin(ang);
    final across = axis.cross(barDir).normalized;
    final s = shape.knobScale;
    final h = (shape.buttonKnob ? 0.04 : 0.07) * s;
    final button = _cylinder(
      origin: base,
      axis: axis,
      radial: V3.xAxis,
      radius: 0.105 * s,
      height: h,
      chamfer: 0.014 * s,
      color: knob,
      spec: 0.32,
      gloss: 36,
    );
    if (shape.buttonKnob) return [button];
    return [
      button,
      _box(
        base + axis * (h + 0.04 * s),
        barDir * (0.098 * s),
        across * (0.03 * s),
        axis * (0.04 * s),
        knob,
        spec: 0.32,
        gloss: 36,
      ),
    ];
  }

  _Mesh _dialMesh() => _cylinder(
        origin: SpeakerGeometry.knobBase(SpeakerGeometry.dialX),
        axis: SpeakerGeometry.roofNormal,
        radial: V3.xAxis,
        radius: 0.07 * shape.dialScale,
        height: 0.03 * shape.dialScale,
        chamfer: 0.01 * shape.dialScale,
        color: dial,
        spec: 0.25,
        gloss: 40,
      );
}

// =============================================================================
// PAINTING HELPERS
// =============================================================================

Path _planePath(SpeakerCamera cam, V3 origin, V3 u, V3 v, List<Offset> pts) {
  final path = Path();
  for (var i = 0; i < pts.length; i++) {
    final s = cam.screen(origin + u * pts[i].dx + v * pts[i].dy);
    i == 0 ? path.moveTo(s.dx, s.dy) : path.lineTo(s.dx, s.dy);
  }
  return path..close();
}

List<Offset> _stadium(double w, double h, {int seg = 10}) {
  final r = math.min(w, h) / 2;
  final pts = <Offset>[];
  final corners = [
    Offset(w / 2 - r, h / 2 - r),
    Offset(-w / 2 + r, h / 2 - r),
    Offset(-w / 2 + r, -h / 2 + r),
    Offset(w / 2 - r, -h / 2 + r),
  ];
  for (var k = 0; k < 4; k++) {
    for (var i = 0; i <= seg; i++) {
      final a = k * math.pi / 2 + (math.pi / 2) * i / seg;
      pts.add(corners[k] + Offset(math.cos(a), math.sin(a)) * r);
    }
  }
  return pts;
}

List<Offset> _circle(Offset c, double r, {int seg = 32}) => [
      for (var i = 0; i < seg; i++)
        c + Offset(math.cos(i * 2 * math.pi / seg), math.sin(i * 2 * math.pi / seg)) * r,
    ];

Color _scaleColor(Color c, double k) => Color.fromARGB(
      255,
      (c.r * 255 * k).round().clamp(0, 255),
      (c.g * 255 * k).round().clamp(0, 255),
      (c.b * 255 * k).round().clamp(0, 255),
    );

/// Body colour of the pose currently being painted (set by the painters).
Color _paintBody = SpeakerGeometry.bodyColor;

Color _faceColor(SpeakerCamera cam, V3 point, V3 normal) => shadeColor(
    _paintBody, cam.rotate(normal), cam.toView(point),
    spec: 0.14, gloss: 18);

/// Draws a recessed feature (groove + darker inset) on a body face.
void _recess(Canvas canvas, SpeakerCamera cam, V3 o, V3 u, V3 v, V3 n,
    List<Offset> outer, List<Offset> inner, {double depth = 0.55}) {
  final base = _faceColor(cam, o, n);
  canvas.drawPath(_planePath(cam, o, u, v, outer),
      Paint()..color = _scaleColor(base, 0.35));
  canvas.drawPath(_planePath(cam, o, u, v, inner),
      Paint()..color = _scaleColor(base, depth + 0.3));
  canvas.drawPath(
    _planePath(cam, o, u, v, outer),
    Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 0.8
      ..color = Colors.white.withValues(alpha: 0.07),
  );
}

// =============================================================================
// PAINTERS
// =============================================================================

/// Shadow, occluded attachments, body shell and its surface details.
class SpeakerBackPainter extends CustomPainter {
  final SpeakerPose pose;
  SpeakerBackPainter(this.pose);

  @override
  void paint(Canvas canvas, Size size) {
    _G.active = pose.shape;
    _paintBody = pose.body;
    final cam = pose.camera;

    // Soft contact shadow on the floor
    final shadow = _planePath(cam, const V3(0, 0, 0), V3.xAxis, V3.zAxis,
        _stadium(_G.width * 1.12, math.max(_G.depth, _G.faceZ * 2) * 1.2, seg: 6));
    // Radial gradient instead of a blur mask filter: a blur costs an extra
    // offscreen pass on every frame.
    final shadowRect = shadow.getBounds().inflate(cam.scale * 0.08);
    canvas.drawOval(
      shadowRect,
      Paint()
        ..shader = RadialGradient(
          colors: [
            Colors.black.withValues(alpha: 0.6),
            Colors.black.withValues(alpha: 0.35),
            Colors.transparent,
          ],
          stops: const [0.0, 0.6, 1.0],
        ).createShader(shadowRect),
    );

    // Controls on a roof slope facing away are hidden behind the body.
    if (!pose.roofVisible) _paintControls(canvas, pose);

    final faceOn = cam.facing(V3(0, 0.4, _G.faceZ), V3.zAxis) > 0;
    if (faceOn) _paintParts(canvas, pose, _attachments(pose.shape)?.back);
    if (!faceOn) _paintParts(canvas, pose, _attachments(pose.shape)?.front);

    _bodyMesh.draw(canvas, cam, tint: pose.body);

    // Whichever half lies behind the body from this side was drawn above; the
    // headphones' far cup comes in front of the body seen from behind.
    if (!faceOn && pose.shape.headband) {
      _paintParts(canvas, pose, _attachments(pose.shape)?.back);
    }

    // Passive radiators on both sides
    for (final sgn in pose.shape.sideHeight > 0 ? [-1.0, 1.0] : const <double>[]) {
      final n = V3(sgn, 0, 0);
      final o = V3(sgn * (_G.width / 2 + 0.001), pose.shape.sideY, 0);
      if (cam.facing(o, n) <= 0) continue;
      _recess(canvas, cam, o, V3.zAxis, V3.yAxis, n,
          _stadium(pose.shape.sideWidth, pose.shape.sideHeight),
          _stadium(pose.shape.sideWidth - 0.04, pose.shape.sideHeight - 0.04));
    }

    _paintFaceDetail(canvas, pose);

    // Back panel: buttons, status LED, USB-C
    const backN = V3(0, 0, -1);
    final backO = V3(0, 0, -_G.depth / 2 - 0.001);
    if (pose.shape.sideHeight > 0 && cam.facing(V3(0, 0.4, -_G.depth / 2), backN) > 0) {
      for (final x in [0.27, 0.15]) {
        _recess(canvas, cam, backO, V3.xAxis, V3.yAxis, backN,
            _circle(Offset(x, 0.18), 0.038), _circle(Offset(x, 0.18), 0.03));
      }
      _recess(canvas, cam, backO, V3.xAxis, V3.yAxis, backN,
          _stadium(0.13, 0.042)
              .map((p) => p + const Offset(-0.24, 0.18))
              .toList(),
          _stadium(0.1, 0.022)
              .map((p) => p + const Offset(-0.24, 0.18))
              .toList());
      canvas.drawPath(
          _planePath(cam, backO, V3.xAxis, V3.yAxis,
              _circle(const Offset(0.04, 0.18), 0.008, seg: 10)),
          Paint()..color = Colors.white.withValues(alpha: 0.5));
    }

    // Back roof: small pill button
    final brMid = V3(0, _G.roofMid.y, -_G.depth / 4) + _G.backRoofNormal * 0.001;
    if (pose.shape.backRoofButton && cam.facing(brMid, _G.backRoofNormal) > 0) {
      _recess(canvas, cam, brMid, V3.xAxis, _G.backRoofUp, _G.backRoofNormal,
          _stadium(0.1, 0.04), _stadium(0.08, 0.024));
    }

    // Front roof: recessed dish around the dial
    if (pose.roofVisible) {
      final o = _G.knobBase(_G.dialX) + _G.roofNormal * 0.001;
      final k = pose.shape.dialScale;
      _recess(canvas, cam, o, V3.xAxis, _G.roofUp, _G.roofNormal,
          _circle(Offset.zero, 0.098 * k), _circle(Offset.zero, 0.088 * k));
    }
  }

  @override
  bool shouldRepaint(SpeakerBackPainter old) => true;
}

/// Lanyard fin plus the controls when they face the camera.
class SpeakerFrontPainter extends CustomPainter {
  final SpeakerPose pose;
  SpeakerFrontPainter(this.pose);

  @override
  void paint(Canvas canvas, Size size) {
    _G.active = pose.shape;
    _paintBody = pose.body;
    final cam = pose.camera;
    final finZ = cam.toView(V3(0, _G.ridgeTop + 0.15, _G.finZ)).z;
    final ctrlZ = cam.toView(SpeakerGeometry.roofMid).z;

    if (pose.roofVisible && ctrlZ < finZ) _paintControls(canvas, pose);
    if (_G.finHeight > 0) _paintFin(canvas, cam);
    if (pose.shape.antenna) {
      for (final m in _antennaMeshes) {
        m.draw(canvas, cam);
      }
    }
    if (pose.roofVisible && ctrlZ >= finZ) _paintControls(canvas, pose);
    final parts = _attachments(pose.shape);
    if (pose.shape.wrappedEarphones) {
      final faceOn = cam.facing(V3(0, 0.4, _G.faceZ), V3.zAxis) > 0;
      _paintParts(canvas, pose, faceOn ? parts?.front : parts?.back);
    }
  }

  @override
  bool shouldRepaint(SpeakerFrontPainter old) => true;
}

void _paintParts(Canvas canvas, SpeakerPose pose, List<(_Mesh, bool)>? parts) {
  if (parts == null) return;
  for (final (mesh, bodyColour) in parts) {
    mesh.draw(canvas, pose.camera, tint: bodyColour ? pose.body : null);
  }
}

void _paintFin(Canvas canvas, SpeakerCamera cam) {
  _finEdgeMesh.draw(canvas, cam, tint: _paintBody);

  final front = cam.facing(V3(0, _G.ridgeTop + 0.15, _G.finZ), V3.zAxis) > 0;
  final z = _G.finZ + (front ? 1 : -1) * _G.finThickness / 2;
  final n = V3(0, 0, front ? 1.0 : -1.0);
  final o = V3(0, 0, z);
  final path = _planePath(cam, o, V3.xAxis, V3.yAxis, _finOutline)
    ..addPath(
        _planePath(cam, o, V3.xAxis, V3.yAxis, _finHole),
        Offset.zero)
    ..fillType = PathFillType.evenOdd;

  final topP = V3(0, _G.ridgeTop + _G.finHeight, z);
  final botP = V3(0, _G.ridgeTop, z);
  canvas.drawPath(
    path,
    Paint()
      ..shader = ui.Gradient.linear(
        cam.screen(topP),
        cam.screen(botP),
        [
          _scaleColor(_faceColor(cam, topP, n), 1.12),
          _scaleColor(_faceColor(cam, botP, n), 0.82),
        ],
      ),
  );
}

/// The detail that sets a model apart, on the front wall.
void _paintFaceDetail(Canvas canvas, SpeakerPose pose) {
  final cam = pose.camera;
  final o = V3(0, 0, _G.faceZ + 0.001);
  if (pose.shape.detail == SpeakerDetail.none) return;
  if (cam.facing(V3(0, 0.4, _G.faceZ), V3.zAxis) <= 0) return;

  void recess(List<Offset> outer, List<Offset> inner) =>
      _recess(canvas, cam, o, V3.xAxis, V3.yAxis, V3.zAxis, outer, inner);
  List<Offset> at(List<Offset> pts, double x, double y) =>
      [for (final p in pts) p + Offset(x, y)];

  switch (pose.shape.detail) {
    case SpeakerDetail.slats:
      for (var i = 0; i < 7; i++) {
        final y = 0.14 + i * 0.075;
        recess(at(_stadium(0.5, 0.034), 0.35, y), at(_stadium(0.47, 0.016), 0.35, y));
      }
    case SpeakerDetail.screws:
      const d = 0.4;
      for (final (sx, sy) in const [(-1, -1), (1, -1), (-1, 1), (1, 1)]) {
        final c = Offset(sx * d, 0.5 + sy * d);
        recess(_circle(c, 0.024, seg: 14), _circle(c, 0.014, seg: 10));
      }
    case SpeakerDetail.woofer:
      const c = Offset(0, 0.26);
      recess(_circle(c, 0.19, seg: 40), _circle(c, 0.17, seg: 40));
      recess(_circle(c, 0.13, seg: 32), _circle(c, 0.115, seg: 32));
      recess(_circle(c, 0.055, seg: 20), _circle(c, 0.045, seg: 20));
    case SpeakerDetail.wheel:
      const c = Offset(0, 0.3);
      final base = _faceColor(cam, o, V3.zAxis);
      Path round(double r) => _planePath(cam, o, V3.xAxis, V3.yAxis, _circle(c, r, seg: 48));
      canvas.drawPath(round(0.235), Paint()..color = _scaleColor(base, 0.62));
      canvas.drawPath(round(0.225), Paint()..color = _scaleColor(base, 1.28));
      canvas.drawPath(round(0.09), Paint()..color = _scaleColor(base, 0.62));
    case SpeakerDetail.cassette:
      recess(at(_stadium(1.02, 0.56), 0, 0.42), at(_stadium(0.98, 0.52), 0, 0.42));
      const reel = Offset(0.25, 0.42);
      recess(_circle(reel, 0.2, seg: 36), _circle(reel, 0.185, seg: 36));
      recess(_circle(reel, 0.075, seg: 20), _circle(reel, 0.06, seg: 20));
      for (var i = 0; i < 6; i++) {
        final a = i * math.pi / 3;
        final d = Offset(math.cos(a), math.sin(a));
        canvas.drawLine(
          cam.screen(o + V3(reel.dx + d.dx * 0.03, reel.dy + d.dy * 0.03, 0)),
          cam.screen(o + V3(reel.dx + d.dx * 0.06, reel.dy + d.dy * 0.06, 0)),
          Paint()
            ..color = Colors.white.withValues(alpha: 0.4)
            ..strokeWidth = math.max(1, cam.scale * 0.009),
        );
      }
      for (var i = 0; i < 4; i++) {
        recess(at(_stadium(0.2, 0.05), -0.36 + i * 0.24, 0.07), at(_stadium(0.18, 0.034), -0.36 + i * 0.24, 0.07));
      }
    case SpeakerDetail.twinWoofers:
      for (final x in [-0.56, 0.56]) {
        final c = Offset(x, 0.4);
        recess(_circle(c, 0.25, seg: 40), _circle(c, 0.225, seg: 40));
        recess(_circle(c, 0.16, seg: 32), _circle(c, 0.145, seg: 32));
        recess(_circle(c, 0.065, seg: 20), _circle(c, 0.052, seg: 20));
      }
    case SpeakerDetail.none:
      break;
  }
}

void _paintControls(Canvas canvas, SpeakerPose pose) {
  final cam = pose.camera;

  void dial() {
    pose._dialMesh().draw(canvas, cam);
    if (!pose.roofVisible) return;
    final c = pose.dialTop;
    final ang = 2.1 - pose.dialTurn * math.pi / 4;
    final ds = pose.shape.dialScale;
    final tip = c + (V3.xAxis * math.cos(ang) + _G.roofUp * math.sin(ang)) * (0.05 * ds);
    canvas.drawLine(
      cam.screen(c + (tip - c) * 0.15),
      cam.screen(tip),
      Paint()
        ..color = pose.dialLit
            ? const Color(0xFFFF3B41)
            : Colors.white.withValues(alpha: 0.9)
        ..strokeWidth = math.max(1.2, cam.scale * 0.011)
        ..strokeCap = StrokeCap.round,
    );

    // Sleep timer: ring of dots around the dial, lit for the time remaining
    final frac = pose.sleepFraction;
    if (frac != null) {
      const n = 12;
      final lit = (frac * n).ceil().clamp(0, n);
      final base = _G.knobBase(_G.dialX) + _G.roofNormal * 0.004;
      final r = math.max(1.0, cam.scale * 0.007 * ds);
      for (var i = 0; i < n; i++) {
        final a = -math.pi / 2 + i * 2 * math.pi / n;
        final p = base + (V3.xAxis * math.cos(a) + _G.roofUp * -math.sin(a)) * (0.112 * ds);
        canvas.drawCircle(
          cam.screen(p),
          r,
          Paint()..color = i < lit ? const Color(0xFFFF3B41) : const Color(0x33FFFFFF),
        );
      }
    }
  }

  void knob() {
    // Red bar knob: base before bar unless seen from below the roof plane.
    final parts = pose._knobMeshes();
    for (final m in pose.roofVisible ? parts : parts.reversed) {
      m.draw(canvas, cam);
    }
  }

  // Farther control first
  if (cam.toView(pose.dialTop).z < cam.toView(pose.knobTop).z) {
    dial();
    knob();
  } else {
    knob();
    dial();
  }
}

// =============================================================================
// FRONT FACE OVERLAY (logo + album art in the recessed aperture)
// =============================================================================

/// Places [child] (sized [faceSize]) onto the speaker's front face.
class SpeakerFrontFace extends StatelessWidget {
  final SpeakerCamera camera;
  final Widget Function(double diameter) artwork;
  final Widget grille;
  final Color logoColor;
  final SpeakerShape shape;

  const SpeakerFrontFace({
    super.key,
    required this.camera,
    required this.artwork,
    required this.grille,
    this.logoColor = const Color(0xB8FFFFFF),
    this.shape = SpeakerShape.classic,
  });

  static double visibility(SpeakerCamera cam, SpeakerShape shape) =>
      cam.facing(V3(0, shape.apertureY, shape.faceZ), V3.zAxis);

  @override
  Widget build(BuildContext context) {
    _G.active = shape;
    final ppu = camera.scale; // widget px per world unit
    final step = 1 / ppu;
    final origin = V3(-_G.width / 2, _G.wallHeight, _G.faceZ + 0.001);
    final matrix =
        camera.planeMatrix(origin, const V3(1, 0, 0) * step, const V3(0, -1, 0) * step);

    // Parallax of the recessed artwork, seen through the aperture.
    final centerW = V3(_G.apertureX, _G.apertureY, _G.faceZ);
    final v = (camera.toView(centerW) * -1).normalized;
    final vu = v.dot(camera.rotate(V3.xAxis));
    final vv = v.dot(camera.rotate(const V3(0, -1, 0)));
    final vn = math.max(0.15, v.dot(camera.rotate(V3.zAxis)));
    final rPx = _G.apertureRadius * ppu;
    final shift = Offset(vu / vn, vv / vn) * (_G.apertureDepth * ppu);
    final clamped = shape.screen
        ? Offset.zero // a flat display has no recess
        : shift.distance > rPx * 0.5
            ? shift / shift.distance * rPx * 0.5
            : shift;

    final light = shadeColor(const Color(0xFFFFFFFF), camera.rotate(V3.zAxis),
            camera.toView(centerW), spec: 0, rim: 0)
        .r;

    final cx = (_G.width / 2 + _G.apertureX) * ppu;
    final cy = (_G.wallHeight - _G.apertureY) * ppu;

    return Transform(
      transform: matrix,
      child: SizedBox(
        width: _G.width * ppu,
        height: _G.wallHeight * ppu,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            Positioned(
              left: cx - rPx,
              top: cy - rPx,
              width: rPx * 2,
              height: rPx * 2,
              child: ClipPath(
                clipper: _ApertureClipper(screen: shape.screen),
                child: DecoratedBox(
                  decoration: const BoxDecoration(
                    gradient: RadialGradient(
                      colors: [Color(0xFF15161A), Color(0xFF060607)],
                    ),
                  ),
                  child: Stack(
                    children: [
                      // Artwork is already round (TrackArtwork clips itself),
                      // so no second clip under the 3D transform.
                      Transform.translate(
                        offset: clamped,
                        child: SizedBox.expand(
                          child: Stack(
                            children: [
                              Positioned.fill(child: RepaintBoundary(child: artwork(rPx * 2))),
                              Positioned.fill(child: RepaintBoundary(child: grille)),
                            ],
                          ),
                        ),
                      ),
                      // Dim the artwork as the face turns from the key light
                      Positioned.fill(
                        child: ColoredBox(
                          color: Colors.black.withValues(
                              alpha: (0.75 - light).clamp(0.0, 0.5)),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            // Aperture lip
            if (!shape.screen)
              Positioned(
                left: cx - rPx - 3,
                top: cy - rPx - 3,
                width: rPx * 2 + 6,
                height: rPx * 2 + 6,
                child: const CustomPaint(painter: _ApertureLipPainter()),
              ),
            // LYRO wordmark
            Positioned(
              left: shape.logoX * ppu,
              top: shape.logoY * ppu,
              child: Text(
                'LYRO',
                style: TextStyle(
                  fontFamily: 'Doto',
                  fontWeight: FontWeight.w700,
                  fontSize: 0.085 * ppu,
                  letterSpacing: 0.012 * ppu,
                  color: logoColor,
                  height: 1,
                  decoration: TextDecoration.none,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Round porthole, or the softly squared corners of a flat display.
class _ApertureClipper extends CustomClipper<Path> {
  final bool screen;
  const _ApertureClipper({required this.screen});

  @override
  Path getClip(Size size) => screen
      ? (Path()..addRRect(RRect.fromRectAndRadius(Offset.zero & size, Radius.circular(size.width * 0.05))))
      : (Path()..addOval(Offset.zero & size));

  @override
  bool shouldReclip(_ApertureClipper old) => old.screen != screen;
}

class _ApertureLipPainter extends CustomPainter {
  const _ApertureLipPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final c = size.center(Offset.zero);
    final r = size.width / 2 - 3;
    final rect = Rect.fromCircle(center: c, radius: r + 2);
    canvas.drawCircle(
      c,
      r + 1,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.5
        ..shader = const SweepGradient(
          colors: [
            Color(0xFF1A1B1F),
            Color(0xFF4A4D57),
            Color(0xFF1A1B1F),
            Color(0xFF0E0F12),
            Color(0xFF1A1B1F),
          ],
          transform: GradientRotation(-math.pi / 4),
        ).createShader(rect),
    );
    // Inner shadow cast by the lip onto the recess (gradient, not a blur)
    final inner = Rect.fromCircle(center: c, radius: r);
    canvas.drawCircle(
      c,
      r,
      Paint()
        ..shader = RadialGradient(
          colors: [
            Colors.transparent,
            Colors.black.withValues(alpha: 0.45),
          ],
          stops: [((r - 8) / r).clamp(0.0, 1.0), 1.0],
        ).createShader(inner),
    );
  }

  @override
  bool shouldRepaint(_ApertureLipPainter old) => false;
}

// =============================================================================
// SOUND WAVES FROM THE SIDE PASSIVE RADIATORS
// =============================================================================

/// Pressure rings that leave the side radiators while music plays, and the
/// radiator membrane pumping in time with them.
///
/// Paint once with [nearSides] = false underneath the body (rings leaving the
/// far side are partly hidden by it) and once with true on top of the body.
class SpeakerSoundWavePainter extends CustomPainter {
  final SpeakerCamera camera;
  final Animation<double> phase; // 0..1 repeating
  final Animation<double> level; // 0 silent .. 1 playing
  final bool nearSides;
  final Color color;
  final SpeakerShape shape;

  static const int _rings = 3;

  SpeakerSoundWavePainter({
    required this.camera,
    required this.phase,
    required this.level,
    required this.nearSides,
    this.color = const Color(0xFFEDEDED),
    this.shape = SpeakerShape.classic,
  }) : super(repaint: Listenable.merge([phase, level]));

  @override
  void paint(Canvas canvas, Size size) {
    final lv = level.value;
    if (lv <= 0.001 || shape.sideHeight == 0) return;
    final p = phase.value;
    final sw = shape.sideWidth, sh = shape.sideHeight;

    for (final sgn in [-1.0, 1.0]) {
      final n = V3(sgn, 0, 0);
      final c = V3(sgn * (shape.width / 2 + 0.002), shape.sideY, 0);
      if ((camera.facing(c, n) > 0) != nearSides) continue;

      if (nearSides) {
        // Membrane thump at each ring emission
        final thump = math.pow(1 - (p * _rings) % 1.0, 4).toDouble() * lv;
        canvas.drawPath(
          _planePath(camera, c + n * (0.012 * thump), V3.zAxis, V3.yAxis,
              _stadium((sw - 0.04) * (1 + 0.05 * thump), (sh - 0.04) * (1 + 0.03 * thump))),
          Paint()..color = color.withValues(alpha: 0.07 * thump),
        );
      }

      for (var i = 0; i < _rings; i++) {
        final t = (p + i / _rings) % 1.0;
        final fade = math.pow(1 - t, 1.6) * math.min(1.0, t * 6) * lv;
        if (fade <= 0.002) continue;
        final o = c + n * (0.04 + t * 0.55);
        final ring = _stadium(sw * (1 + t * 1.5), sh * (1 + t * 0.7));
        canvas.drawPath(
          _planePath(camera, o, V3.zAxis, V3.yAxis, ring),
          Paint()
            ..style = PaintingStyle.stroke
            ..strokeWidth = camera.scale * (0.008 + 0.012 * (1 - t))
            ..color = color.withValues(alpha: 0.45 * fade),
        );
      }
    }
  }

  @override
  bool shouldRepaint(SpeakerSoundWavePainter old) =>
      old.camera != camera ||
      old.nearSides != nearSides ||
      old.color != color ||
      old.shape != shape;
}

// =============================================================================
// EXPORT FOR THE WEB PLAYER
// =============================================================================

/// One flat, convex polygon on a body surface, as a mesh.
_Mesh _fan(List<Offset> pts, V3 o, V3 u, V3 v, V3 n, Color color) {
  final mesh = _Mesh(color, spec: 0.1, gloss: 18);
  var c = Offset.zero;
  for (final p in pts) {
    c += p;
  }
  c = c / pts.length.toDouble();
  V3 at(Offset p) => o + u * p.dx + v * p.dy;
  final hub = mesh.vertex(at(c), n);
  final ids = [for (final p in pts) mesh.vertex(at(p), n)];
  for (var i = 0; i < ids.length; i++) {
    mesh.tri(hub, ids[i], ids[(i + 1) % ids.length], n);
  }
  return mesh;
}

/// Distance from [c] along [d] to the edge of the closed loop [loop].
double _rayToLoop(Offset c, Offset d, List<Offset> loop) {
  var best = double.infinity;
  for (var i = 0; i < loop.length; i++) {
    final a = loop[i], b = loop[(i + 1) % loop.length], e = b - a;
    final det = d.dx * e.dy - d.dy * e.dx;
    if (det.abs() < 1e-9) continue;
    final ac = a - c;
    final t = (ac.dx * e.dy - ac.dy * e.dx) / det;
    final s = (ac.dx * d.dy - ac.dy * d.dx) / det;
    if (t > 0 && s >= -1e-9 && s <= 1 + 1e-9) best = math.min(best, t);
  }
  return best;
}

/// The flat faces of the fin: a strip from the hole out to the outline.
List<_Mesh> _finFaces() {
  final center = Offset(_G.active.holeX, _G.ridgeTop + _G.active.holeRise);
  return [
    for (final side in [1.0, -1.0])
      () {
        final mesh = _Mesh(_G.bodyColor, spec: 0.14, gloss: 18);
        final z = _G.finZ + side * _G.finThickness / 2;
        final n = V3(0, 0, side);
        const steps = 96;
        final rings = <(int, int)>[];
        for (var i = 0; i < steps; i++) {
          final a = i * 2 * math.pi / steps;
          final d = Offset(math.cos(a), math.sin(a));
          final inner = center + d * _rayToLoop(center, d, _finHole);
          final outer = center + d * _rayToLoop(center, d, _finOutline);
          rings.add((mesh.vertex(V3(inner.dx, inner.dy, z), n), mesh.vertex(V3(outer.dx, outer.dy, z), n)));
        }
        for (var i = 0; i < steps; i++) {
          final (a, b) = rings[i];
          final (c, d) = rings[(i + 1) % steps];
          mesh.quad(a, b, d, c, n);
        }
        return mesh;
      }(),
  ];
}

/// The model of [shape] as plain data, so the web player draws exactly what
/// the app draws. `role` says which colour a part takes: body, knob, dial or
/// its own [c]; `shade` scales the body colour for details; `spin` marks the
/// knob parts that turn.
Map<String, dynamic> speakerModelJson(SpeakerShape shape) {
  _G.active = shape;
  final parts = <Map<String, dynamic>>[];
  List<double> flat(Iterable<V3> vs) => [
        for (final v in vs) ...[
          (v.x * 1e4).round() / 1e4,
          (v.y * 1e4).round() / 1e4,
          (v.z * 1e4).round() / 1e4,
        ],
      ];
  void add(_Mesh m, String role, {double shade = 1, bool spin = false, Color? color}) {
    parts.add({
      'role': role,
      'shade': shade,
      if (spin) 'spin': true,
      if (role == 'fixed') 'c': _hex(color ?? m.color),
      's': m.spec,
      'g': m.gloss,
      'p': flat(m.positions),
      'n': flat(m.normals),
      'i': m.indices,
    });
  }

  add(_bodyMesh, 'body');
  if (_G.finHeight > 0) {
    add(_finEdgeMesh, 'body');
    for (final m in _finFaces()) {
      add(m, 'body');
    }
  }
  if (shape.antenna) {
    for (final m in _antennaMeshes) {
      add(m, 'fixed');
    }
  }
  final attached = _attachments(shape);
  for (final (mesh, bodyColour) in [...?attached?.back, ...?attached?.front]) {
    add(mesh, bodyColour ? 'body' : 'fixed');
  }

  // Details painted on the body in the app: flat polygons here
  void decal(List<Offset> pts, V3 o, V3 u, V3 v, V3 n, double shade, {double lift = 0.002}) =>
      add(_fan(pts, o + n * lift, u, v, n, _G.bodyColor), 'body', shade: shade);
  void recessed(List<Offset> outer, List<Offset> inner, V3 o, V3 u, V3 v, V3 n) {
    decal(outer, o, u, v, n, 0.35);
    decal(inner, o, u, v, n, 0.8, lift: 0.004);
  }

  for (final sgn in shape.sideHeight > 0 ? [-1.0, 1.0] : const <double>[]) {
    recessed(_stadium(shape.sideWidth, shape.sideHeight), _stadium(shape.sideWidth - 0.04, shape.sideHeight - 0.04),
        V3(sgn * _G.width / 2, shape.sideY, 0), V3.zAxis, V3.yAxis, V3(sgn, 0, 0));
  }
  final face = V3(0, 0, _G.faceZ);
  List<Offset> at(List<Offset> pts, double x, double y) => [for (final p in pts) p + Offset(x, y)];
  void front(List<Offset> outer, List<Offset> inner) => recessed(outer, inner, face, V3.xAxis, V3.yAxis, V3.zAxis);
  switch (shape.detail) {
    case SpeakerDetail.slats:
      for (var i = 0; i < 7; i++) {
        final y = 0.14 + i * 0.075;
        front(at(_stadium(0.5, 0.034), 0.35, y), at(_stadium(0.47, 0.016), 0.35, y));
      }
    case SpeakerDetail.screws:
      for (final (sx, sy) in const [(-1, -1), (1, -1), (-1, 1), (1, 1)]) {
        final c = Offset(sx * 0.4, 0.5 + sy * 0.4);
        front(_circle(c, 0.024, seg: 14), _circle(c, 0.014, seg: 10));
      }
    case SpeakerDetail.woofer:
      const c = Offset(0, 0.26);
      front(_circle(c, 0.19, seg: 40), _circle(c, 0.17, seg: 40));
      decal(_circle(c, 0.13, seg: 32), face, V3.xAxis, V3.yAxis, V3.zAxis, 0.5, lift: 0.006);
      decal(_circle(c, 0.055, seg: 20), face, V3.xAxis, V3.yAxis, V3.zAxis, 0.3, lift: 0.008);
    case SpeakerDetail.wheel:
      const c = Offset(0, 0.3);
      decal(_circle(c, 0.235, seg: 48), face, V3.xAxis, V3.yAxis, V3.zAxis, 0.62);
      decal(_circle(c, 0.225, seg: 48), face, V3.xAxis, V3.yAxis, V3.zAxis, 1.28, lift: 0.004);
      decal(_circle(c, 0.09, seg: 28), face, V3.xAxis, V3.yAxis, V3.zAxis, 0.62, lift: 0.006);
    case SpeakerDetail.cassette:
      front(at(_stadium(1.02, 0.56), 0, 0.42), at(_stadium(0.98, 0.52), 0, 0.42));
      const reel = Offset(0.25, 0.42);
      decal(_circle(reel, 0.2, seg: 36), face, V3.xAxis, V3.yAxis, V3.zAxis, 0.3, lift: 0.006);
      decal(_circle(reel, 0.075, seg: 20), face, V3.xAxis, V3.yAxis, V3.zAxis, 0.9, lift: 0.008);
      for (var i = 0; i < 4; i++) {
        front(at(_stadium(0.2, 0.05), -0.36 + i * 0.24, 0.07), at(_stadium(0.18, 0.034), -0.36 + i * 0.24, 0.07));
      }
    case SpeakerDetail.twinWoofers:
      for (final x in const [-0.56, 0.56]) {
        final c = Offset(x, 0.4);
        front(_circle(c, 0.25, seg: 40), _circle(c, 0.225, seg: 40));
        decal(_circle(c, 0.16, seg: 32), face, V3.xAxis, V3.yAxis, V3.zAxis, 0.5, lift: 0.006);
        decal(_circle(c, 0.065, seg: 20), face, V3.xAxis, V3.yAxis, V3.zAxis, 0.3, lift: 0.008);
      }
    case SpeakerDetail.none:
      break;
  }

  // Controls, on their panel
  final pose = SpeakerPose(
    camera: SpeakerCamera(yaw: 0, pitch: 0, scale: 1, center: Offset.zero),
    knobPress: 0,
    knobTurn: 0,
    dialTurn: 0,
    dialLit: false,
    shape: shape,
  );
  final dialBase = _G.knobBase(_G.dialX);
  decal(_circle(Offset.zero, 0.098 * shape.dialScale), dialBase, V3.xAxis, _G.roofUp, _G.roofNormal, 0.35);
  add(pose._dialMesh(), 'dial');
  const ang = 2.1;
  final tip = (V3.xAxis * math.cos(ang) + _G.roofUp * math.sin(ang)) * (0.05 * shape.dialScale);
  add(
    _box(pose.dialTop + tip * 0.55, tip * 0.45, _G.roofNormal.cross(tip).normalized * (0.005 * shape.dialScale),
        _G.roofNormal * 0.002, Colors.white),
    'fixed',
  );
  final knob = pose._knobMeshes();
  for (final (i, m) in knob.indexed) {
    add(m, 'knob', spin: i > 0);
  }

  final roofNormal = _G.roofNormal, base = _G.knobBase(_G.knobX), top = pose.knobTop;
  return {
    'label': shape.label,
    'parts': parts,
    'width': shape.width,
    'depth': math.max(shape.depth, shape.faceZ * 2),
    'wall': shape.wallHeight,
    'faceZ': shape.faceZ,
    'screen': shape.screen,
    'aperture': [shape.apertureX, shape.apertureY, shape.apertureRadius],
    'logo': [shape.logoX, shape.logoY],
    'pivotY': shape.pivotY,
    'fit': [shape.fitWidth, shape.fitHeight],
    'knobBase': flat([base]),
    'knobAxis': flat([roofNormal]),
    'knobTop': flat([top]),
    'knobRadius': 0.15 * shape.knobScale,
  };
}

String _hex(Color c) =>
    '#${(c.toARGB32() & 0xFFFFFF).toRadixString(16).padLeft(6, '0')}';

/// A colour as the web player's `#rrggbb`.
String speakerColorHex(Color c) => _hex(c);
