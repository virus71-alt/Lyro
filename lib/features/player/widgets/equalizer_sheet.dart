import 'package:flutter/material.dart';

import '../../eq/equalizer_screen.dart';

/// Entry point kept for existing callers: opens the IEM equalizer screen
/// (the old static preset sheet wasn't connected to any audio).
class EqualizerSheet {
  EqualizerSheet._();

  static Future<void> show(BuildContext context) =>
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => const EqualizerScreen()));
}
