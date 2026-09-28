import 'package:flutter/material.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);
    return Scaffold(
      backgroundColor: colors.bg,
      body: Padding(
        padding: const EdgeInsets.all(28),
        child: EmptyState(
          icon: Icons.settings_rounded,
          headline: 'Settings',
          body: 'API keys and app preferences — coming soon.',
          colors: colors,
        ),
      ),
    );
  }
}
