import 'package:flutter/material.dart';
import 'nothing_colors.dart';

/// Typography specification for the Nothing UI Design System.
/// Follows §2.3 of SPEC.md.
class NothingTypography {
  NothingTypography._();

  static const String fontDisplay = 'Doto';
  static const String fontUi = 'Geist';
  static const String fontMono = 'GeistMono';
  static const String fontEditorial = 'Newsreader';

  // --- Display / Dot-matrix (Doto) ---
  static TextStyle displayHero({
    required bool isDark,
    Color? color,
    double fontSize = 34,
  }) =>
      TextStyle(
        fontFamily: fontDisplay,
        fontSize: fontSize,
        fontWeight: FontWeight.w800,
        fontVariations: const [
          FontVariation('wght', 850.0),
          FontVariation('ROND', 100.0),
        ],
        letterSpacing: 2.0,
        color: color ?? (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay),
      );

  static TextStyle displayTitle({
    required bool isDark,
    Color? color,
    double fontSize = 20,
  }) =>
      TextStyle(
        fontFamily: fontDisplay,
        fontSize: fontSize,
        fontWeight: FontWeight.w700,
        fontVariations: const [
          FontVariation('wght', 750.0),
          FontVariation('ROND', 100.0),
        ],
        letterSpacing: 1.2,
        color: color ?? (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay),
      );

  /// Bold dot-matrix style for stats, numbers, and prominent sublines (e.g. 188, 41, 68)
  static TextStyle dottedBold({
    required bool isDark,
    double fontSize = 14,
    Color? color,
    double letterSpacing = 0.6,
  }) =>
      TextStyle(
        fontFamily: fontDisplay,
        fontSize: fontSize,
        fontWeight: FontWeight.w700,
        fontVariations: const [
          FontVariation('wght', 800.0),
          FontVariation('ROND', 100.0),
        ],
        letterSpacing: letterSpacing,
        color: color ?? (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay),
      );

  static TextStyle displayClock({
    required bool isDark,
    double size = 14,
    Color? color,
  }) =>
      TextStyle(
        fontFamily: fontDisplay,
        fontSize: size,
        fontWeight: FontWeight.w700,
        fontVariations: const [
          FontVariation('wght', 750.0),
          FontVariation('ROND', 100.0),
        ],
        letterSpacing: 0.8,
        color: color ?? (isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary),
      );

  // --- Headlines (Geist SemiBold) ---
  static TextStyle headlineLarge({required bool isDark}) => TextStyle(
        fontFamily: fontUi,
        fontSize: 20,
        fontWeight: FontWeight.w600,
        letterSpacing: -0.4,
        color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
      );

  static TextStyle headlineMedium({required bool isDark}) => TextStyle(
        fontFamily: fontUi,
        fontSize: 16,
        fontWeight: FontWeight.w600,
        letterSpacing: -0.2,
        color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
      );

  static TextStyle headlineSmall({required bool isDark}) => TextStyle(
        fontFamily: fontUi,
        fontSize: 14,
        fontWeight: FontWeight.w600,
        letterSpacing: -0.1,
        color: isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay,
      );

  // --- Body UI (Geist Regular) ---
  static TextStyle bodyLarge({required bool isDark}) => TextStyle(
        fontFamily: fontUi,
        fontSize: 15,
        fontWeight: FontWeight.w400,
        height: 1.4,
        color: isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary,
      );

  static TextStyle bodyMedium({required bool isDark}) => TextStyle(
        fontFamily: fontUi,
        fontSize: 13,
        fontWeight: FontWeight.w400,
        height: 1.35,
        color: isDark ? NothingColors.darkPrimary : NothingColors.lightPrimary,
      );

  static TextStyle bodySmall({required bool isDark}) => TextStyle(
        fontFamily: fontUi,
        fontSize: 12,
        fontWeight: FontWeight.w400,
        height: 1.3,
        color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
      );

  // --- Mono / Technical Data (GeistMono) ---
  // "Labels are --f-mono, uppercase, 9–10px, letter-spacing .08–.14em, color --secondary/--muted."
  static TextStyle monoLabel({required bool isDark, Color? color}) => TextStyle(
        fontFamily: fontMono,
        fontSize: 10,
        fontWeight: FontWeight.w500,
        letterSpacing: 1.4,
        color: color ?? (isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary),
      );

  static TextStyle monoTimestamp({required bool isDark}) => TextStyle(
        fontFamily: fontMono,
        fontSize: 11,
        fontWeight: FontWeight.w400,
        letterSpacing: 0.5,
        color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
      );

  static TextStyle monoBadge({required bool isDark, Color? color}) => TextStyle(
        fontFamily: fontMono,
        fontSize: 9,
        fontWeight: FontWeight.w500,
        letterSpacing: 1.2,
        color: color ?? (isDark ? NothingColors.darkDisplay : NothingColors.lightDisplay),
      );

  // --- Editorial Accent (Newsreader Italic) ---
  // "Editorial italic is a page-level accent, at most one short expressive line per view."
  static TextStyle editorialHero({required bool isDark}) => TextStyle(
        fontFamily: fontEditorial,
        fontStyle: FontStyle.italic,
        fontSize: 18,
        fontWeight: FontWeight.w400,
        color: isDark ? NothingColors.darkSecondary : NothingColors.lightSecondary,
      );
}

