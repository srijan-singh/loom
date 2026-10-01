import 'package:flutter/material.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

class LibraryScreen extends StatelessWidget {
  const LibraryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);
    return Scaffold(
      backgroundColor: colors.bg,
      body: Padding(
        padding: const EdgeInsets.all(28),
        child: EmptyState(
          icon: Icons.collections_bookmark_rounded,
          headline: 'Library',
          body: 'Skills, MCP connections, and agent definitions — coming soon.',
          colors: colors,
        ),
      ),
    );
  }
}
