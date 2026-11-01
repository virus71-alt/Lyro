import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'nothing_colors.dart';
import 'nothing_typography.dart';

class NothingTheme {
  NothingTheme._();

  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: NothingColors.darkBg,
      primaryColor: NothingColors.darkDisplay,
      colorScheme: const ColorScheme.dark(
        primary: NothingColors.darkDisplay,
        onPrimary: NothingColors.darkBg,
        surface: NothingColors.darkSurface,
        onSurface: NothingColors.darkPrimary,
        error: NothingColors.darkError,
        onError: Colors.white,
      ),
      dividerColor: NothingColors.darkLine,
      fontFamily: NothingTypography.fontUi,
      appBarTheme: const AppBarTheme(
        backgroundColor: NothingColors.darkBg,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        systemOverlayStyle: SystemUiOverlayStyle.light,
        iconTheme: IconThemeData(color: NothingColors.darkDisplay),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: NothingColors.darkSurface,
        selectedItemColor: NothingColors.darkDisplay,
        unselectedItemColor: NothingColors.darkSecondary,
      ),
      cardTheme: CardThemeData(
        color: NothingColors.darkGlass,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(NothingColors.radiusMd),
          side: const BorderSide(color: NothingColors.darkLine, width: 1),
        ),
      ),
    );
  }

  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      scaffoldBackgroundColor: NothingColors.lightBg,
      primaryColor: NothingColors.lightDisplay,
      colorScheme: const ColorScheme.light(
        primary: NothingColors.lightDisplay,
        onPrimary: Colors.white,
        surface: NothingColors.lightSurface,
        onSurface: NothingColors.lightPrimary,
        error: NothingColors.lightError,
        onError: Colors.white,
      ),
      dividerColor: NothingColors.lightLine,
      fontFamily: NothingTypography.fontUi,
      appBarTheme: const AppBarTheme(
        backgroundColor: NothingColors.lightBg,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        systemOverlayStyle: SystemUiOverlayStyle.dark,
        iconTheme: IconThemeData(color: NothingColors.lightDisplay),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: NothingColors.lightSurface,
        selectedItemColor: NothingColors.lightDisplay,
        unselectedItemColor: NothingColors.lightSecondary,
      ),
      cardTheme: CardThemeData(
        color: NothingColors.lightGlass,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(NothingColors.radiusMd),
          side: const BorderSide(color: NothingColors.lightLine, width: 1),
        ),
      ),
    );
  }
}

/// Extension on BuildContext for effortless access to Nothing design tokens
extension NothingContextExtension on BuildContext {
  bool get isDark => Theme.of(this).brightness == Brightness.dark;

  Color get nothingBg => isDark ? NothingColors.darkBg : NothingColors.lightBg;
  Color get nothingSurface => isDark ? NothingColors.darkSurface : NothingColors.lightSurface;
  Color get nothingRaised => isDark ? NothingColors.darkRaised : NothingColors.lightRaised;
  Color get nothingLine => isDark ? NothingColors.darkLine : NothingColors.lightLine;
  Color get nothingLine2 => isDark ? NothingColors.darkLine2 : NothingColors.lightLine2;
  Color get nothingPrimary => isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary;
  Color get nothingSecondary => isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary;
  Color get nothingMuted => isDark ? NothingColors.darkMuted : NothingColors.lightMuted;
  Color get nothingDisplay => isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay;
  Color get nothingGlass => isDark ? NothingColors.darkGlass : NothingColors.lightGlass;
  Color get nothingGlassBrd => isDark ? NothingColors.darkGlassBrd : NothingColors.lightGlassBrd;
  Color get nothingAccent => isDark ? NothingColors.darkAccentText : NothingColors.lightAccent;
}

