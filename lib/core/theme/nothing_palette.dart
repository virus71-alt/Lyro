import 'package:flutter/material.dart';

import 'nothing_colors.dart';

/// The Nothing colour set for the current theme, for widgets that would
/// otherwise pick `darkX` / `lightX` by hand (sheets, dialogs).
class NothingPalette {
  final bool dark;
  final Color bg, surface, raised, line, line2, muted, secondary, display, onDisplay;

  const NothingPalette._(this.dark, this.bg, this.surface, this.raised, this.line, this.line2,
      this.muted, this.secondary, this.display, this.onDisplay);

  static const _dark = NothingPalette._(
    true,
    NothingColors.darkBg,
    NothingColors.darkSurface,
    NothingColors.darkRaised,
    NothingColors.darkLine,
    NothingColors.darkLine2,
    NothingColors.darkMuted,
    NothingColors.darkSecondary,
    NothingColors.darkDisplay,
    Colors.black,
  );

  static const _light = NothingPalette._(
    false,
    NothingColors.lightBg,
    NothingColors.lightSurface,
    NothingColors.lightRaised,
    NothingColors.lightLine,
    NothingColors.lightLine2,
    NothingColors.lightMuted,
    NothingColors.lightSecondary,
    NothingColors.lightDisplay,
    Colors.white,
  );

  static NothingPalette of(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? _dark : _light;
}
