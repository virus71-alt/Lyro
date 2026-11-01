import 'package:flutter/material.dart';

import '../../data/repositories/preferences_repository.dart';
import '../../data/services/audio_handler.dart';

/// CMF-style speaker colourways for the Now Playing speaker.
enum SpeakerColorway {
  graphite('Graphite', Color(0xFF3B3D44), Color(0xFFE3262D), Color(0xFF26272C)),
  ash('Ash', Color(0xFFB9BBC0), Color(0xFFE3262D), Color(0xFF8E9096)),
  orange('Orange', Color(0xFFE5672B), Color(0xFF2B2C30), Color(0xFFC4501C)),
  green('Green', Color(0xFF7E9B7A), Color(0xFFF2F2EE), Color(0xFF5E7A5B));

  final String label;
  final Color body;
  final Color knob;
  final Color dial;
  const SpeakerColorway(this.label, this.body, this.knob, this.dial);
}

/// Player look-and-feel and playback preferences.
class AppSettingsProvider extends ChangeNotifier {
  final PreferencesRepository prefsRepo;
  final LyroAudioHandler audioHandler;

  late bool _reduceMotion = prefsRepo.getReduceMotion();
  late int _speakerColor = prefsRepo.getSpeakerColor();
  late int _fadeSeconds = prefsRepo.getFadeSeconds();
  late bool _onboarded = prefsRepo.getOnboarded();

  AppSettingsProvider({required this.prefsRepo, required this.audioHandler}) {
    audioHandler.fade = Duration(seconds: _fadeSeconds);
  }

  /// User setting only; combine with the OS setting via [motionReduced].
  bool get reduceMotion => _reduceMotion;

  /// -1 = follow the theme (graphite in dark, ash in light).
  int get speakerColorIndex => _speakerColor;
  int get fadeSeconds => _fadeSeconds;
  bool get onboarded => _onboarded;

  /// Whether to calm animations: the in-app switch or Android's "remove
  /// animations" accessibility setting.
  bool motionReduced(BuildContext context) =>
      _reduceMotion || MediaQuery.disableAnimationsOf(context);

  SpeakerColorway colorway(Brightness brightness) {
    if (_speakerColor >= 0 && _speakerColor < SpeakerColorway.values.length) {
      return SpeakerColorway.values[_speakerColor];
    }
    return brightness == Brightness.light ? SpeakerColorway.ash : SpeakerColorway.graphite;
  }

  Future<void> setReduceMotion(bool v) async {
    _reduceMotion = v;
    notifyListeners();
    await prefsRepo.setReduceMotion(v);
  }

  Future<void> setSpeakerColor(int index) async {
    _speakerColor = index;
    notifyListeners();
    await prefsRepo.setSpeakerColor(index);
  }

  Future<void> setFadeSeconds(int seconds) async {
    _fadeSeconds = seconds;
    audioHandler.fade = Duration(seconds: seconds);
    notifyListeners();
    await prefsRepo.setFadeSeconds(seconds);
  }

  Future<void> completeOnboarding() async {
    _onboarded = true;
    notifyListeners();
    await prefsRepo.setOnboarded();
  }
}
