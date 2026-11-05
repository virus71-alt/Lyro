import 'dart:math' as math;

/// One parametric filter, as in AutoEQ's `ParametricEQ.txt`.
class PeqFilter {
  final String type; // 'PK' peaking, 'LS' low shelf, 'HS' high shelf
  final double fc;
  final double gain;
  final double q;

  const PeqFilter(this.type, this.fc, this.gain, this.q);

  PeqFilter copyWith({String? type, double? fc, double? gain, double? q}) =>
      PeqFilter(type ?? this.type, fc ?? this.fc, gain ?? this.gain, q ?? this.q);

  bool sameAs(PeqFilter o) => type == o.type && fc == o.fc && gain == o.gain && q == o.q;

  Map<String, dynamic> toJson() => {'type': type, 'fc': fc, 'gain': gain, 'q': q};

  static PeqFilter fromJson(Map<String, dynamic> j) => PeqFilter(
        j['type'] as String,
        (j['fc'] as num).toDouble(),
        (j['gain'] as num).toDouble(),
        (j['q'] as num).toDouble(),
      );

  /// Magnitude response in dB at [f] Hz (RBJ audio-EQ cookbook biquads).
  double responseDb(double f, {double sampleRate = 48000}) {
    if (gain == 0) return 0;
    final a = math.pow(10, gain / 40).toDouble();
    final w0 = 2 * math.pi * fc / sampleRate;
    final alpha = math.sin(w0) / (2 * q);
    final cosw = math.cos(w0);
    double b0, b1, b2, a0, a1, a2;
    switch (type) {
      case 'LS':
        final sq = 2 * math.sqrt(a) * alpha;
        b0 = a * ((a + 1) - (a - 1) * cosw + sq);
        b1 = 2 * a * ((a - 1) - (a + 1) * cosw);
        b2 = a * ((a + 1) - (a - 1) * cosw - sq);
        a0 = (a + 1) + (a - 1) * cosw + sq;
        a1 = -2 * ((a - 1) + (a + 1) * cosw);
        a2 = (a + 1) + (a - 1) * cosw - sq;
      case 'HS':
        final sq = 2 * math.sqrt(a) * alpha;
        b0 = a * ((a + 1) + (a - 1) * cosw + sq);
        b1 = -2 * a * ((a - 1) + (a + 1) * cosw);
        b2 = a * ((a + 1) + (a - 1) * cosw - sq);
        a0 = (a + 1) - (a - 1) * cosw + sq;
        a1 = 2 * ((a - 1) - (a + 1) * cosw);
        a2 = (a + 1) - (a - 1) * cosw - sq;
      default: // PK
        b0 = 1 + alpha * a;
        b1 = -2 * cosw;
        b2 = 1 - alpha * a;
        a0 = 1 + alpha / a;
        a1 = -2 * cosw;
        a2 = 1 - alpha / a;
    }
    // |H(e^jw)| at the evaluation frequency
    final w = 2 * math.pi * f / sampleRate;
    final c1 = math.cos(w), s1 = math.sin(w);
    final c2 = math.cos(2 * w), s2 = math.sin(2 * w);
    final nr = b0 + b1 * c1 + b2 * c2, ni = -(b1 * s1 + b2 * s2);
    final dr = a0 + a1 * c1 + a2 * c2, di = -(a1 * s1 + a2 * s2);
    final mag2 = (nr * nr + ni * ni) / (dr * dr + di * di);
    return 10 * math.log(mag2) / math.ln10;
  }
}

/// An IEM's correction profile (AutoEQ result or a built-in sound profile).
class IemProfile {
  final String id;
  final String brand;
  final String name;
  final String source; // who measured it, or 'Lyro' for sound profiles
  final String? note;
  final double preamp;
  final List<PeqFilter> filters;
  final bool measured;

  const IemProfile({
    required this.id,
    required this.brand,
    required this.name,
    required this.source,
    required this.preamp,
    required this.filters,
    this.note,
    this.measured = true,
  });

  String get title => brand.isEmpty ? name : '$brand $name';

  Map<String, dynamic> toJson() => {
        'id': id,
        'brand': brand,
        'name': name,
        'source': source,
        'note': note,
        'preamp': preamp,
        'measured': measured,
        'filters': [for (final f in filters) f.toJson()],
      };

  static IemProfile fromJson(Map<String, dynamic> j, {String? id}) => IemProfile(
        id: id ?? j['id'] as String? ?? '${j['brand']}|${j['name']}',
        brand: j['brand'] as String? ?? '',
        name: j['name'] as String,
        source: j['source'] as String? ?? 'AutoEQ',
        note: j['note'] as String?,
        preamp: (j['preamp'] as num?)?.toDouble() ?? 0,
        measured: j['measured'] as bool? ?? true,
        filters: [for (final f in j['filters'] as List) PeqFilter.fromJson(f as Map<String, dynamic>)],
      );

  /// Parses AutoEQ's `ParametricEQ.txt` (Preamp + Filter lines).
  static IemProfile? parseAutoEq(String text,
      {required String id, required String brand, required String name, required String source}) {
    final pre = RegExp(r'Preamp:\s*(-?[\d.]+)\s*dB').firstMatch(text);
    final filterRe = RegExp(
        r'Filter\s+\d+:\s+ON\s+(PK|LSC|HSC|LS|HS)\s+Fc\s+([\d.]+)\s+Hz\s+Gain\s+(-?[\d.]+)\s+dB(?:\s+Q\s+([\d.]+))?');
    final filters = [
      for (final m in filterRe.allMatches(text))
        PeqFilter(
          const {'LSC': 'LS', 'HSC': 'HS'}[m.group(1)] ?? m.group(1)!,
          double.parse(m.group(2)!),
          double.parse(m.group(3)!),
          double.parse(m.group(4) ?? '0.7'),
        ),
    ];
    if (filters.isEmpty) return null;
    return IemProfile(
      id: id,
      brand: brand,
      name: name,
      source: source,
      preamp: double.tryParse(pre?.group(1) ?? '') ?? 0,
      filters: filters,
    );
  }
}

/// Turns filters into what the native engine applies: a 32-band graphic
/// curve from 20 Hz to 20 kHz plus a negative input gain for headroom.
class EqCurve {
  EqCurve._();

  static const int bandCount = 32;
  static const double fMin = 20, fMax = 20000;

  /// Upper edge of each band (what Android's DynamicsProcessing expects).
  static final List<double> bandEdges = [
    for (var i = 1; i <= bandCount; i++) fMin * math.pow(fMax / fMin, i / bandCount).toDouble(),
  ];

  /// Geometric centre of each band.
  static final List<double> bandCenters = [
    for (var i = 0; i < bandCount; i++) fMin * math.pow(fMax / fMin, (i + 0.5) / bandCount).toDouble(),
  ];

  static double responseDb(List<PeqFilter> filters, double f) =>
      filters.fold(0.0, (sum, flt) => sum + flt.responseDb(f));

  static List<double> bandGains(List<PeqFilter> filters) =>
      [for (final f in bandCenters) responseDb(filters, f)];

  /// Headroom so the loudest boost can't clip (never positive).
  static double preampFor(List<double> gains) =>
      -gains.fold<double>(0, (m, g) => math.max(m, g));
}

/// Manual fine-tune: a classic 10-band graphic EQ layered on top.
class ManualBands {
  ManualBands._();

  static const List<double> freqs = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];
  static const List<String> labels = ['31', '62', '125', '250', '500', '1K', '2K', '4K', '8K', '16K'];
  static const double maxDb = 12;

  static List<PeqFilter> filters(List<double> gains) => [
        for (var i = 0; i < freqs.length; i++)
          if (gains[i] != 0) PeqFilter('PK', freqs[i], gains[i], 1.41),
      ];
}

/// Limits of the free parametric bands in the Sandbox.
class SandboxBands {
  SandboxBands._();

  static const int max = 10;
  static const double minHz = 20, maxHz = 20000, minQ = 0.2, maxQ = 8;

  /// Rounded and kept in range, so saved values stay tidy.
  static PeqFilter tidy(PeqFilter f) => PeqFilter(
        f.type,
        f.fc.clamp(minHz, maxHz).roundToDouble(),
        (f.gain.clamp(-ManualBands.maxDb, ManualBands.maxDb) * 10).roundToDouble() / 10,
        (f.q.clamp(minQ, maxQ) * 100).roundToDouble() / 100,
      );
}

/// A sound the user saved: their IEM choice, fine-tune and Sandbox bands.
class UserEqPreset {
  final String id;
  final String name;
  final String iemId;
  final List<double> manual;
  final List<PeqFilter> sandbox;

  const UserEqPreset({
    required this.id,
    required this.name,
    required this.iemId,
    required this.manual,
    required this.sandbox,
  });

  UserEqPreset copyWith({String? name, String? iemId, List<double>? manual, List<PeqFilter>? sandbox}) =>
      UserEqPreset(
        id: id,
        name: name ?? this.name,
        iemId: iemId ?? this.iemId,
        manual: manual ?? this.manual,
        sandbox: sandbox ?? this.sandbox,
      );

  /// Whether this is exactly the given sound.
  bool matches(String iem, List<double> gains, List<PeqFilter> bands) {
    if (iem != iemId || gains.length != manual.length || bands.length != sandbox.length) return false;
    for (var i = 0; i < gains.length; i++) {
      if (gains[i] != manual[i]) return false;
    }
    for (var i = 0; i < bands.length; i++) {
      if (!bands[i].sameAs(sandbox[i])) return false;
    }
    return true;
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'iem': iemId,
        'manual': manual,
        'sandbox': [for (final f in sandbox) f.toJson()],
      };

  static UserEqPreset fromJson(Map<String, dynamic> j) => UserEqPreset(
        id: j['id'] as String,
        name: j['name'] as String,
        iemId: j['iem'] as String,
        manual: [for (final g in j['manual'] as List) (g as num).toDouble()],
        sandbox: [for (final f in j['sandbox'] as List? ?? const []) PeqFilter.fromJson(f as Map<String, dynamic>)],
      );
}

/// Built-in sound profiles for IEMs without a measurement.
class SoundProfiles {
  SoundProfiles._();

  static const List<IemProfile> all = [
    IemProfile(id: 'sound|flat', brand: '', name: 'Flat (no correction)', source: 'Lyro', preamp: 0, filters: [], measured: false),
    IemProfile(
      id: 'sound|bass',
      brand: '',
      name: 'Bass boost',
      source: 'Lyro',
      preamp: 0,
      measured: false,
      filters: [PeqFilter('LS', 105, 5.5, 0.7), PeqFilter('PK', 220, -1.0, 1.0)],
    ),
    IemProfile(
      id: 'sound|warm',
      brand: '',
      name: 'Warm & smooth',
      source: 'Lyro',
      preamp: 0,
      measured: false,
      filters: [PeqFilter('LS', 150, 3.0, 0.7), PeqFilter('PK', 3500, -2.0, 1.2), PeqFilter('HS', 9000, -2.5, 0.7)],
    ),
    IemProfile(
      id: 'sound|vocal',
      brand: '',
      name: 'Vocal focus',
      source: 'Lyro',
      preamp: 0,
      measured: false,
      filters: [PeqFilter('LS', 120, -2.0, 0.7), PeqFilter('PK', 1800, 2.5, 1.0), PeqFilter('PK', 3200, 1.5, 1.4)],
    ),
    IemProfile(
      id: 'sound|vshape',
      brand: '',
      name: 'V-shape (fun)',
      source: 'Lyro',
      preamp: 0,
      measured: false,
      filters: [PeqFilter('LS', 110, 4.5, 0.7), PeqFilter('PK', 1000, -2.5, 0.8), PeqFilter('HS', 8000, 3.5, 0.7)],
    ),
    IemProfile(
      id: 'sound|tame',
      brand: '',
      name: 'Tame bright treble',
      source: 'Lyro',
      preamp: 0,
      measured: false,
      filters: [PeqFilter('PK', 6000, -3.0, 2.0), PeqFilter('HS', 10000, -3.0, 0.7)],
    ),
  ];
}
