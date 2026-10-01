import 'package:flutter/material.dart';

// ---------------------------------------------------------------------------
// Design-token colour palette — matches the issue #8 UI reference exactly.
// Two static instances: LoomColors.light and LoomColors.dark.
// Never hardcode hex in widgets; always read from the theme extension.
// ---------------------------------------------------------------------------

@immutable
class LoomColors extends ThemeExtension<LoomColors> {
  const LoomColors({
    required this.bg,
    required this.surface,
    required this.sunken,
    required this.ink,
    required this.ink2,
    required this.ink3,
    required this.line,
    required this.lineStrong,
    required this.accent,
    required this.onAccent,
    required this.accentTint,
    required this.accentInk,
    required this.success,
    required this.successTint,
    required this.danger,
    required this.dangerTint,
    required this.warning,
    required this.warningTint,
    required this.rail,
  });

  final Color bg;
  final Color surface;
  final Color sunken;
  final Color ink;
  final Color ink2;
  final Color ink3;
  final Color line;
  final Color lineStrong;
  final Color accent;
  final Color onAccent;
  final Color accentTint;
  final Color accentInk;
  final Color success;
  final Color successTint;
  final Color danger;
  final Color dangerTint;
  final Color warning;
  final Color warningTint;
  final Color rail;

  // -------------------------------------------------------------------------
  // Light palette (primary)
  // -------------------------------------------------------------------------
  static const light = LoomColors(
    bg: Color(0xFFF1F2F5),
    surface: Color(0xFFFFFFFF),
    sunken: Color(0xFFF6F7F9),
    ink: Color(0xFF161A22),
    ink2: Color(0xFF4A5261),
    ink3: Color(0xFF6B7485),
    line: Color(0xFFE1E4EA),
    lineStrong: Color(0xFFC4CAD6),
    accent: Color(0xFF3846B0),
    onAccent: Color(0xFFFFFFFF),
    accentTint: Color(0xFFE8EAF8),
    accentInk: Color(0xFF2A3690),
    success: Color(0xFF2E7D4F),
    successTint: Color(0xFFE4F2E9),
    danger: Color(0xFFB23A2E),
    dangerTint: Color(0xFFFBE9E6),
    warning: Color(0xFF8A5F00),
    warningTint: Color(0xFFFBF1D6),
    rail: Color(0xFFF6F7F9),
  );

  // -------------------------------------------------------------------------
  // Dark palette
  // -------------------------------------------------------------------------
  static const dark = LoomColors(
    bg: Color(0xFF0F1218),
    surface: Color(0xFF171B23),
    sunken: Color(0xFF1C212B),
    ink: Color(0xFFECEFF5),
    ink2: Color(0xFFA9B1C1),
    ink3: Color(0xFF8790A2),
    line: Color(0xFF2A303C),
    lineStrong: Color(0xFF3B4353),
    accent: Color(0xFF8E9CF5),
    onAccent: Color(0xFF0F1218),
    accentTint: Color(0xFF232A52),
    accentInk: Color(0xFFB9C2FA),
    success: Color(0xFF6FCB94),
    successTint: Color(0xFF163224),
    danger: Color(0xFFF08A7D),
    dangerTint: Color(0xFF3A1D19),
    warning: Color(0xFFE5B94E),
    warningTint: Color(0xFF33280C),
    rail: Color(0xFF1C212B),
  );

  // -------------------------------------------------------------------------
  // ThemeExtension boilerplate
  // -------------------------------------------------------------------------
  @override
  LoomColors copyWith({
    Color? bg, Color? surface, Color? sunken,
    Color? ink, Color? ink2, Color? ink3,
    Color? line, Color? lineStrong,
    Color? accent, Color? onAccent, Color? accentTint, Color? accentInk,
    Color? success, Color? successTint,
    Color? danger, Color? dangerTint,
    Color? warning, Color? warningTint,
    Color? rail,
  }) => LoomColors(
    bg: bg ?? this.bg,
    surface: surface ?? this.surface,
    sunken: sunken ?? this.sunken,
    ink: ink ?? this.ink,
    ink2: ink2 ?? this.ink2,
    ink3: ink3 ?? this.ink3,
    line: line ?? this.line,
    lineStrong: lineStrong ?? this.lineStrong,
    accent: accent ?? this.accent,
    onAccent: onAccent ?? this.onAccent,
    accentTint: accentTint ?? this.accentTint,
    accentInk: accentInk ?? this.accentInk,
    success: success ?? this.success,
    successTint: successTint ?? this.successTint,
    danger: danger ?? this.danger,
    dangerTint: dangerTint ?? this.dangerTint,
    warning: warning ?? this.warning,
    warningTint: warningTint ?? this.warningTint,
    rail: rail ?? this.rail,
  );

  @override
  LoomColors lerp(LoomColors? other, double t) {
    if (other == null) return this;
    return LoomColors(
      bg: Color.lerp(bg, other.bg, t)!,
      surface: Color.lerp(surface, other.surface, t)!,
      sunken: Color.lerp(sunken, other.sunken, t)!,
      ink: Color.lerp(ink, other.ink, t)!,
      ink2: Color.lerp(ink2, other.ink2, t)!,
      ink3: Color.lerp(ink3, other.ink3, t)!,
      line: Color.lerp(line, other.line, t)!,
      lineStrong: Color.lerp(lineStrong, other.lineStrong, t)!,
      accent: Color.lerp(accent, other.accent, t)!,
      onAccent: Color.lerp(onAccent, other.onAccent, t)!,
      accentTint: Color.lerp(accentTint, other.accentTint, t)!,
      accentInk: Color.lerp(accentInk, other.accentInk, t)!,
      success: Color.lerp(success, other.success, t)!,
      successTint: Color.lerp(successTint, other.successTint, t)!,
      danger: Color.lerp(danger, other.danger, t)!,
      dangerTint: Color.lerp(dangerTint, other.dangerTint, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      warningTint: Color.lerp(warningTint, other.warningTint, t)!,
      rail: Color.lerp(rail, other.rail, t)!,
    );
  }

  /// Convenience accessor — reads the extension from the nearest [BuildContext].
  static LoomColors of(BuildContext context) =>
      Theme.of(context).extension<LoomColors>()!;
}

// ---------------------------------------------------------------------------
// Spacing and radius tokens
// ---------------------------------------------------------------------------

abstract class LoomSpace {
  static const double s4 = 4;
  static const double s8 = 8;
  static const double s12 = 12;
  static const double s16 = 16;
  static const double s24 = 24;
  static const double s32 = 32;
}

abstract class LoomRadius {
  static const double control = 6;
  static const double card = 10;
  static const double window = 14;
}

abstract class LoomSize {
  static const double rail = 72;
  static const double control = 36;
  static const double controlCompact = 30;
  static const Size minWindow = Size(960, 640);
}

// ---------------------------------------------------------------------------
// ThemeData builders
// ---------------------------------------------------------------------------

abstract class LoomTheme {
  LoomTheme._();

  static ThemeData get light => _build(
        brightness: Brightness.light,
        colors: LoomColors.light,
      );

  static ThemeData get dark => _build(
        brightness: Brightness.dark,
        colors: LoomColors.dark,
      );

  static ThemeData _build({
    required Brightness brightness,
    required LoomColors colors,
  }) =>
      ThemeData(
        useMaterial3: true,
        brightness: brightness,
        colorScheme: ColorScheme(
          brightness: brightness,
          primary: colors.accent,
          onPrimary: colors.onAccent,
          secondary: colors.accentTint,
          onSecondary: colors.accentInk,
          error: colors.danger,
          onError: colors.surface,
          surface: colors.surface,
          onSurface: colors.ink,
        ),
        scaffoldBackgroundColor: colors.bg,
        cardColor: colors.surface,
        dividerColor: colors.line,
        extensions: [colors],
        textTheme: TextTheme(
          // display — 28/34/600 for welcome screen
          displayLarge: TextStyle(
            fontSize: 28,
            height: 34 / 28,
            fontWeight: FontWeight.w600,
            letterSpacing: -0.015 * 28,
            color: colors.ink,
          ),
          // title — 20/26/600
          titleLarge: TextStyle(
            fontSize: 20,
            height: 26 / 20,
            fontWeight: FontWeight.w600,
            color: colors.ink,
          ),
          // titleSmall — 16/22/600
          titleMedium: TextStyle(
            fontSize: 16,
            height: 22 / 16,
            fontWeight: FontWeight.w600,
            color: colors.ink,
          ),
          // body — 14/22/400
          bodyLarge: TextStyle(
            fontSize: 14,
            height: 22 / 14,
            color: colors.ink,
          ),
          bodyMedium: TextStyle(
            fontSize: 14,
            height: 22 / 14,
            color: colors.ink2,
          ),
          // label — 13/18/500
          labelLarge: TextStyle(
            fontSize: 13,
            height: 18 / 13,
            fontWeight: FontWeight.w500,
            color: colors.ink,
          ),
          // caption — 12/16/400
          bodySmall: TextStyle(
            fontSize: 12,
            height: 16 / 12,
            color: colors.ink3,
          ),
          labelSmall: TextStyle(
            fontSize: 11,
            color: colors.ink3,
            letterSpacing: 0.5,
          ),
        ),
      );
}
