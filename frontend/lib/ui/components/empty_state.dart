import 'package:flutter/material.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Shown when a list or section has no items to display.
/// Icon + headline + body copy + optional single action.
class EmptyState extends StatelessWidget {
  final IconData icon;
  final String headline;
  final String body;
  final String? actionLabel;
  final VoidCallback? onAction;
  final LoomColors? colors;

  const EmptyState({
    required this.icon,
    required this.headline,
    required this.body,
    this.actionLabel,
    this.onAction,
    this.colors,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final c = colors ?? LoomColors.of(context);

    return Center(
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 16),
        decoration: BoxDecoration(
          border: Border.all(
              color: c.lineStrong,
              style: BorderStyle.solid),
          borderRadius: BorderRadius.circular(LoomRadius.card),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: c.sunken,
                shape: BoxShape.circle,
              ),
              child: Icon(icon, size: 20, color: c.ink2),
            ),
            const SizedBox(height: 12),
            Text(
              headline,
              style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                  color: c.ink),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 4),
            Text(
              body,
              style: TextStyle(fontSize: 13.5, color: c.ink2),
              textAlign: TextAlign.center,
            ),
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(height: 14),
              GestureDetector(
                onTap: onAction,
                child: Container(
                  height: LoomSize.controlCompact,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  decoration: BoxDecoration(
                    color: Colors.transparent,
                    borderRadius:
                        BorderRadius.circular(LoomRadius.control),
                    border: Border.all(color: c.lineStrong),
                  ),
                  child: Center(
                    child: Text(
                      actionLabel!,
                      style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                          color: c.ink2),
                    ),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
