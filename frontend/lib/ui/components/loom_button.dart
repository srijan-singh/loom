import 'package:flutter/material.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

enum LoomButtonVariant { primary, secondary, destructive, ghost }

/// Loom-styled button: primary / secondary / destructive / ghost.
class LoomButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;
  final LoomButtonVariant variant;
  final bool loading;
  final Widget? icon;
  final bool compact;

  const LoomButton({
    required this.label,
    this.onPressed,
    this.variant = LoomButtonVariant.primary,
    this.loading = false,
    this.icon,
    this.compact = false,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);
    final h = compact ? LoomSize.controlCompact : LoomSize.control;

    final (bg, fg, border) = switch (variant) {
      LoomButtonVariant.primary =>
        (colors.accent, colors.onAccent, colors.accent),
      LoomButtonVariant.secondary =>
        (colors.surface, colors.ink2, colors.lineStrong),
      LoomButtonVariant.destructive =>
        (colors.danger, colors.surface, colors.danger),
      LoomButtonVariant.ghost =>
        (Colors.transparent, colors.accentInk, Colors.transparent),
    };

    final content = loading
        ? SizedBox(
            width: 14,
            height: 14,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation(fg),
            ),
          )
        : Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (icon != null) ...[icon!, const SizedBox(width: 6)],
              Text(label,
                  style: TextStyle(
                      color: fg,
                      fontSize: compact ? 13 : 14,
                      fontWeight: FontWeight.w500)),
            ],
          );

    return GestureDetector(
      onTap: (onPressed != null && !loading) ? onPressed : null,
      child: AnimatedOpacity(
        opacity: (onPressed == null || loading) ? 0.5 : 1.0,
        duration: const Duration(milliseconds: 150),
        child: Container(
          height: h,
          padding: EdgeInsets.symmetric(horizontal: compact ? 12 : 16),
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(LoomRadius.control),
            border: Border.all(color: border),
          ),
          child: Center(child: content),
        ),
      ),
    );
  }
}
