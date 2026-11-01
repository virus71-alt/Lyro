import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

/// Real-time procedural 3D model of the CMF speaker, rendered with Canvas.
///
/// World space: x → right, y → up, z → towards the front face. The body is a
/// house-shaped prism (pentagon profile extruded along x) with rounded edges,
/// a lanyard fin standing on the ridge, a red bar knob and a rotary dial on
/// the front roof, oval passive radiators on both sides and ports on the back.

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
    this.pivotY = SpeakerGeometry.pivotY,
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

  void draw(Canvas canvas, SpeakerCamera cam) {
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

class SpeakerGeometry {
  static const double width = 1.0;
  static const double wallHeight = 0.98;
  static const double depth = 0.74;
  static const double rise = 0.23;
  static const double cornerRadius = 0.075;
  static const double bevel = 0.05;
  static const double pivotY = 0.74;

  static const double finHeight = 0.36;
  static const double finThickness = 0.055;
  static const double finHalfWidth = 0.43;
  static const double finCorner = 0.09;
  static const double holeX = 0.27;
  static const double holeRise = 0.2;
  static const double holeRadius = 0.085;

  // Front-face speaker aperture
  static const double apertureX = 0.02;
  static const double apertureY = 0.47;
  static const double apertureRadius = 0.36;
  static const double apertureDepth = 0.05;

  static const double knobX = 0.27;
  static const double dialX = -0.28;

  static const Color bodyColor = Color(0xFF3B3D44);
  static const Color knobColor = Color(0xFFE3262D);
  static const Color dialColor = Color(0xFF26272C);

  static final V3 roofUp = const V3(0, rise, -depth / 2).normalized;
  static final V3 roofNormal = const V3(0, depth / 2, rise).normalized;
  static const V3 roofMid = V3(0, wallHeight + rise * 0.5, depth / 4);

  static final V3 backRoofUp = const V3(0, rise, depth / 2).normalized;
  static final V3 backRoofNormal = const V3(0, depth / 2, -rise).normalized;

  static const List<Offset> profile = [
    Offset(-depth / 2, 0),
    Offset(depth / 2, 0),
    Offset(depth / 2, wallHeight),
    Offset(0, wallHeight + rise),
    Offset(-depth / 2, wallHeight),
  ];

  /// Height of the rounded ridge where the fin is mounted.
  static final double ridgeTop = () {
    final pts = _roundedProfile(profile, cornerRadius, 1);
    return pts.map((p) => p.center.dy + cornerRadius * p.normal.dy).reduce(math.max);
  }();

  static V3 knobBase(double x) => V3(x, roofMid.y, roofMid.z);
}

/// Rounded-edge body: profile rings swept along x with a quarter-round bevel
/// into the flat side caps.
final _Mesh _bodyMesh = () {
  final mesh = _Mesh(_G.bodyColor, spec: 0.14, gloss: 18);
  final prof = _roundedProfile(_G.profile, _G.cornerRadius, 5);
  const steps = 3;
  final center = V3(0, _G.wallHeight * 0.55, 0);

  final rings = <List<int>>[];
  final ringSpec = <(double, double)>[
    for (var k = steps; k >= 0; k--) (-1.0, k * math.pi / 2 / steps),
    for (var k = 0; k <= steps; k++) (1.0, k * math.pi / 2 / steps),
  ];
  for (final (sgn, th) in ringSpec) {
    final x = sgn * (_G.width / 2 - _G.bevel + _G.bevel * math.sin(th));
    final rr = _G.cornerRadius - _G.bevel * (1 - math.cos(th));
    rings.add([
      for (final p in prof)
        mesh.vertex(
          V3(x, p.center.dy + p.normal.dy * rr, p.center.dx + p.normal.dx * rr),
          V3(sgn * math.sin(th), math.cos(th) * p.normal.dy,
              math.cos(th) * p.normal.dx),
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
  // Flat side caps
  for (final sgn in [-1.0, 1.0]) {
    final ring = sgn < 0 ? rings.first : rings.last;
    final n = V3(sgn, 0, 0);
    final ids = [for (final i in ring) mesh.vertex(mesh.positions[i], n)];
    final c = mesh.vertex(V3(sgn * _G.width / 2, _G.wallHeight * 0.55, 0), n);
    for (var i = 0; i < m; i++) {
      mesh.tri(c, ids[i], ids[(i + 1) % m], n);
    }
  }
  return mesh;
}();

/// Fin outline in (x, y), counter-clockwise.
final List<Offset> _finOutline = () {
  final yb = SpeakerGeometry.ridgeTop - 0.004;
  final yt = SpeakerGeometry.ridgeTop + _G.finHeight;
  const hw = _G.finHalfWidth, c = _G.finCorner;
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

Offset get _holeCenter => Offset(
    SpeakerGeometry.holeX, SpeakerGeometry.ridgeTop + SpeakerGeometry.holeRise);

final _Mesh _finEdgeMesh = () {
  final mesh = _Mesh(_G.bodyColor, spec: 0.14, gloss: 18);
  const t = _G.finThickness / 2;
  void strip(List<Offset> loop, bool inward) {
    for (var i = 0; i < loop.length; i++) {
      final a = loop[i], b = loop[(i + 1) % loop.length];
      final d = b - a;
      var n = V3(d.dy, -d.dx, 0).normalized;
      if (inward) n = n * -1;
      final q = [
        mesh.vertex(V3(a.dx, a.dy, t), n),
        mesh.vertex(V3(b.dx, b.dy, t), n),
        mesh.vertex(V3(b.dx, b.dy, -t), n),
        mesh.vertex(V3(a.dx, a.dy, -t), n),
      ];
      mesh.quad(q[0], q[1], q[2], q[3], n);
    }
  }

  strip(_finOutline, false);
  final hole = [
    for (var i = 0; i < 24; i++)
      _holeCenter +
          Offset(math.cos(i * math.pi / 12), math.sin(i * math.pi / 12)) *
              _G.holeRadius,
  ];
  strip(hole, true); // CCW loop; material is outside the hole
  return mesh;
}();

// =============================================================================
// SCENE STATE
// =============================================================================

class SpeakerPose {
  final SpeakerCamera camera;
  final double knobPress; // 0..1 depression
  final double knobTurn; // 0..1 (paused → playing)
  final double dialTurn; // 0..1 (shuffle off → on)
  final bool shuffle;

  const SpeakerPose({
    required this.camera,
    required this.knobPress,
    required this.knobTurn,
    required this.dialTurn,
    required this.shuffle,
  });

  bool get roofVisible =>
      camera.facing(SpeakerGeometry.roofMid, SpeakerGeometry.roofNormal) > 0;

  V3 get knobTop {
    final base = SpeakerGeometry.knobBase(SpeakerGeometry.knobX);
    return base + SpeakerGeometry.roofNormal * (0.12 - 0.025 * knobPress);
  }

  V3 get dialTop => SpeakerGeometry.knobBase(SpeakerGeometry.dialX) +
      SpeakerGeometry.roofNormal * 0.03;

  List<_Mesh> _knobMeshes() {
    final axis = SpeakerGeometry.roofNormal;
    final base = SpeakerGeometry.knobBase(SpeakerGeometry.knobX) -
        axis * (0.025 * knobPress);
    final r2 = axis.cross(V3.xAxis).normalized;
    final ang = -0.55 + knobTurn * math.pi / 2 + knobPress * 0.3;
    final barDir = V3.xAxis * math.cos(ang) + r2 * math.sin(ang);
    final across = axis.cross(barDir).normalized;
    const h = 0.07;
    return [
      _cylinder(
        origin: base,
        axis: axis,
        radial: V3.xAxis,
        radius: 0.105,
        height: h,
        chamfer: 0.014,
        color: SpeakerGeometry.knobColor,
        spec: 0.32,
        gloss: 36,
      ),
      _box(
        base + axis * (h + 0.04),
        barDir * 0.098,
        across * 0.03,
        axis * 0.04,
        SpeakerGeometry.knobColor,
        spec: 0.32,
        gloss: 36,
      ),
    ];
  }

  _Mesh _dialMesh() => _cylinder(
        origin: SpeakerGeometry.knobBase(SpeakerGeometry.dialX),
        axis: SpeakerGeometry.roofNormal,
        radial: V3.xAxis,
        radius: 0.07,
        height: 0.03,
        chamfer: 0.01,
        color: SpeakerGeometry.dialColor,
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

Color _faceColor(SpeakerCamera cam, V3 point, V3 normal) => shadeColor(
    SpeakerGeometry.bodyColor, cam.rotate(normal), cam.toView(point),
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
    final cam = pose.camera;

    // Soft contact shadow on the floor
    final shadow = _planePath(cam, const V3(0, 0, 0), V3.xAxis, V3.zAxis,
        _stadium(_G.width * 1.12, _G.depth * 1.2, seg: 6));
    canvas.drawPath(
      shadow,
      Paint()
        ..color = Colors.black.withValues(alpha: 0.6)
        ..maskFilter = MaskFilter.blur(BlurStyle.normal, cam.scale * 0.06),
    );

    // Controls on a roof slope facing away are hidden behind the body.
    if (!pose.roofVisible) _paintControls(canvas, pose);

    _bodyMesh.draw(canvas, cam);

    // Passive radiators on both sides
    for (final sgn in [-1.0, 1.0]) {
      final n = V3(sgn, 0, 0);
      final o = V3(sgn * (_G.width / 2 + 0.001), 0.46, 0);
      if (cam.facing(o, n) <= 0) continue;
      _recess(canvas, cam, o, V3.zAxis, V3.yAxis, n, _stadium(0.27, 0.6),
          _stadium(0.23, 0.56));
    }

    // Back panel: buttons, status LED, USB-C
    const backN = V3(0, 0, -1);
    const backO = V3(0, 0, -_G.depth / 2 - 0.001);
    if (cam.facing(const V3(0, 0.4, -_G.depth / 2), backN) > 0) {
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
    final brMid = V3(0, _G.wallHeight + _G.rise * 0.5, -_G.depth / 4) +
        _G.backRoofNormal * 0.001;
    if (cam.facing(brMid, _G.backRoofNormal) > 0) {
      _recess(canvas, cam, brMid, V3.xAxis, _G.backRoofUp, _G.backRoofNormal,
          _stadium(0.1, 0.04), _stadium(0.08, 0.024));
    }

    // Front roof: recessed dish around the dial
    if (pose.roofVisible) {
      final o = _G.knobBase(_G.dialX) + _G.roofNormal * 0.001;
      _recess(canvas, cam, o, V3.xAxis, _G.roofUp, _G.roofNormal,
          _circle(Offset.zero, 0.098), _circle(Offset.zero, 0.088));
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
    final cam = pose.camera;
    final finZ = cam.toView(V3(0, SpeakerGeometry.ridgeTop + 0.15, 0)).z;
    final ctrlZ = cam.toView(SpeakerGeometry.roofMid).z;

    if (pose.roofVisible && ctrlZ < finZ) _paintControls(canvas, pose);
    _paintFin(canvas, cam);
    if (pose.roofVisible && ctrlZ >= finZ) _paintControls(canvas, pose);
  }

  @override
  bool shouldRepaint(SpeakerFrontPainter old) => true;
}

void _paintFin(Canvas canvas, SpeakerCamera cam) {
  _finEdgeMesh.draw(canvas, cam);

  final front = cam.facing(V3(0, _G.ridgeTop + 0.15, 0), V3.zAxis) > 0;
  final z = (front ? 1 : -1) * _G.finThickness / 2;
  final n = V3(0, 0, front ? 1.0 : -1.0);
  final o = V3(0, 0, z);
  final path = _planePath(cam, o, V3.xAxis, V3.yAxis, _finOutline)
    ..addPath(
        _planePath(cam, o, V3.xAxis, V3.yAxis,
            _circle(_holeCenter, _G.holeRadius, seg: 24)),
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

void _paintControls(Canvas canvas, SpeakerPose pose) {
  final cam = pose.camera;

  void dial() {
    pose._dialMesh().draw(canvas, cam);
    if (!pose.roofVisible) return;
    final c = pose.dialTop;
    final ang = 2.1 - pose.dialTurn * math.pi / 4;
    final tip = c + (V3.xAxis * math.cos(ang) + _G.roofUp * math.sin(ang)) * 0.05;
    canvas.drawLine(
      cam.screen(c + (tip - c) * 0.15),
      cam.screen(tip),
      Paint()
        ..color = pose.shuffle
            ? const Color(0xFFFF3B41)
            : Colors.white.withValues(alpha: 0.9)
        ..strokeWidth = math.max(1.2, cam.scale * 0.011)
        ..strokeCap = StrokeCap.round,
    );
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

  const SpeakerFrontFace({
    super.key,
    required this.camera,
    required this.artwork,
    required this.grille,
  });

  static double visibility(SpeakerCamera cam) => cam.facing(
      const V3(0, SpeakerGeometry.apertureY, SpeakerGeometry.depth / 2),
      V3.zAxis);

  @override
  Widget build(BuildContext context) {
    final ppu = camera.scale; // widget px per world unit
    final step = 1 / ppu;
    final origin = V3(-_G.width / 2, _G.wallHeight, _G.depth / 2 + 0.001);
    final matrix =
        camera.planeMatrix(origin, const V3(1, 0, 0) * step, const V3(0, -1, 0) * step);

    // Parallax of the recessed artwork, seen through the aperture.
    final centerW = const V3(_G.apertureX, _G.apertureY, _G.depth / 2);
    final v = (camera.toView(centerW) * -1).normalized;
    final vu = v.dot(camera.rotate(V3.xAxis));
    final vv = v.dot(camera.rotate(const V3(0, -1, 0)));
    final vn = math.max(0.15, v.dot(camera.rotate(V3.zAxis)));
    final rPx = _G.apertureRadius * ppu;
    final shift = Offset(vu / vn, vv / vn) * (_G.apertureDepth * ppu);
    final clamped = shift.distance > rPx * 0.5
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
              child: ClipOval(
                child: DecoratedBox(
                  decoration: const BoxDecoration(
                    gradient: RadialGradient(
                      colors: [Color(0xFF15161A), Color(0xFF060607)],
                    ),
                  ),
                  child: Stack(
                    children: [
                      Transform.translate(
                        offset: clamped,
                        child: ClipOval(
                          child: Stack(
                            children: [
                              Positioned.fill(child: artwork(rPx * 2)),
                              Positioned.fill(child: grille),
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
            Positioned(
              left: cx - rPx - 3,
              top: cy - rPx - 3,
              width: rPx * 2 + 6,
              height: rPx * 2 + 6,
              child: const CustomPaint(painter: _ApertureLipPainter()),
            ),
            // LYRO wordmark
            Positioned(
              left: 0.08 * ppu,
              top: 0.05 * ppu,
              child: Text(
                'LYRO',
                style: TextStyle(
                  fontFamily: 'Doto',
                  fontWeight: FontWeight.w700,
                  fontSize: 0.085 * ppu,
                  letterSpacing: 0.012 * ppu,
                  color: Colors.white.withValues(alpha: 0.72),
                  height: 1,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
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
    // Inner shadow cast by the lip onto the recess
    canvas.drawCircle(
      c,
      r - 2,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 6
        ..color = Colors.black.withValues(alpha: 0.45)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 3),
    );
  }

  @override
  bool shouldRepaint(_ApertureLipPainter old) => false;
}
