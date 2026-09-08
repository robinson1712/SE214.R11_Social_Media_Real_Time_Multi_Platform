import 'package:flutter/material.dart';

/// Facebook-style palette: blue primary, light-grey scaffold background,
/// white cards, subtle borders instead of heavy shadows.
class AppTheme {
  AppTheme._();

  static const Color fbBlue = Color(0xFF0866FF);
  static const Color scaffoldGrey = Color(0xFFF0F2F5);
  static const Color cardBorder = Color(0xFFDADDE1);
  static const Color textSecondary = Color(0xFF65676B);

  static ThemeData get light => ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: fbBlue,
          brightness: Brightness.light,
        ),
        scaffoldBackgroundColor: scaffoldGrey,
        primaryColor: fbBlue,
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.white,
          foregroundColor: Colors.black87,
          elevation: 0,
          surfaceTintColor: Colors.white,
        ),
        cardTheme: CardThemeData(
          color: Colors.white,
          elevation: 0,
          margin: EdgeInsets.zero,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
            side: const BorderSide(color: cardBorder),
          ),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: fbBlue,
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(6),
            ),
            padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: scaffoldGrey,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: BorderSide.none,
          ),
          contentPadding:
              const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        ),
        dividerTheme: const DividerThemeData(color: cardBorder, thickness: 1),
      );
}
