import 'package:flutter/material.dart';
import 'package:loom_ui/ui/components/loom_button.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Reusable confirmation dialog with title, message, cancel, and confirm action.
///
/// Use [ConfirmDialog.show] to display modally and await a boolean result.
class ConfirmDialog extends StatelessWidget {
  final String title;
  final String message;
  final String confirmLabel;
  final bool isDestructive;

  const ConfirmDialog({
    super.key,
    required this.title,
    required this.message,
    this.confirmLabel = 'Confirm',
    this.isDestructive = false,
  });

  /// Shows the dialog and returns `true` if the user confirmed, `false`
  /// (or `null` dismissed to `false`) if cancelled.
  static Future<bool> show(
    BuildContext context, {
    required String title,
    required String message,
    String confirmLabel = 'Confirm',
    bool isDestructive = false,
  }) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (_) => ConfirmDialog(
        title: title,
        message: message,
        confirmLabel: confirmLabel,
        isDestructive: isDestructive,
      ),
    );
    return result ?? false;
  }

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);

    return Dialog(
      backgroundColor: colors.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(LoomRadius.window),
        side: BorderSide(color: colors.line),
      ),
      child: Padding(
        padding: const EdgeInsets.all(LoomSpace.s24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
                color: colors.ink,
              ),
            ),
            const SizedBox(height: LoomSpace.s8),
            Text(
              message,
              style: TextStyle(fontSize: 14, color: colors.ink2),
            ),
            const SizedBox(height: LoomSpace.s24),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                LoomButton(
                  label: 'Cancel',
                  variant: LoomButtonVariant.secondary,
                  compact: true,
                  onPressed: () => Navigator.of(context).pop(false),
                ),
                const SizedBox(width: LoomSpace.s8),
                LoomButton(
                  label: confirmLabel,
                  variant: isDestructive
                      ? LoomButtonVariant.destructive
                      : LoomButtonVariant.primary,
                  compact: true,
                  onPressed: () => Navigator.of(context).pop(true),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
