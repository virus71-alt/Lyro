import 'package:flutter/material.dart';
import '../../data/repositories/preferences_repository.dart';

class ThemeProvider extends ChangeNotifier {
  final PreferencesRepository _prefsRepo;
  ThemeMode _themeMode = ThemeMode.system;

  ThemeProvider(this._prefsRepo) {
    _themeMode = _prefsRepo.getThemeMode();
  }

  ThemeMode get themeMode => _themeMode;

  Future<void> setThemeMode(ThemeMode mode) async {
    if (_themeMode == mode) return;
    _themeMode = mode;
    await _prefsRepo.setThemeMode(mode);
    notifyListeners();
  }
}

