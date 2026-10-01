import 'package:flutter/material.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

class WorkspacesScreen extends StatelessWidget {
  const WorkspacesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);
    return Scaffold(
      backgroundColor: colors.bg,
      body: Padding(
        padding: const EdgeInsets.all(28),
        child: EmptyState(
          icon: Icons.workspaces_rounded,
          headline: 'Workspaces',
          body: 'Create and manage your agent workspaces — coming soon.',
          colors: colors,
        ),
      ),
    );
  }
}
