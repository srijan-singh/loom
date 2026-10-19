import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Renders [data] as formatted Markdown using `flutter_markdown`.
class MarkdownPreview extends StatelessWidget {
  final String data;

  const MarkdownPreview({super.key, required this.data});

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);
    final theme = Theme.of(context).textTheme;

    return MarkdownBody(
      data: data,
      styleSheet: MarkdownStyleSheet(
        p: theme.bodyLarge?.copyWith(color: colors.ink),
        h1: theme.titleLarge?.copyWith(color: colors.ink),
        h2: theme.titleMedium?.copyWith(color: colors.ink),
        h3: theme.labelLarge?.copyWith(color: colors.ink),
        code: TextStyle(
          fontFamily: 'monospace',
          fontSize: 13,
          backgroundColor: colors.sunken,
          color: colors.ink,
        ),
        codeblockDecoration: BoxDecoration(
          color: colors.sunken,
          borderRadius: BorderRadius.circular(LoomRadius.control),
          border: Border.all(color: colors.line),
        ),
        blockquoteDecoration: BoxDecoration(
          border: Border(
            left: BorderSide(color: colors.lineStrong, width: 3),
          ),
        ),
        blockquotePadding: const EdgeInsets.only(left: LoomSpace.s12),
      ),
    );
  }
}
