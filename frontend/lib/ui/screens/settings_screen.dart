import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:loom_ui/models/llm_connection.dart';
import 'package:loom_ui/providers/llm_providers.dart';
import 'package:loom_ui/ui/components/confirm_dialog.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/components/llm_connection_form.dart';
import 'package:loom_ui/ui/components/loom_button.dart';
import 'package:loom_ui/ui/components/skeleton_loader.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colors = LoomColors.of(context);
    final connectionsAsync = ref.watch(llmConnectionsProvider);

    return Scaffold(
      backgroundColor: colors.bg,
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(28),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── Section header ──────────────────────────────────────────────
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Model connections',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w600,
                          color: colors.ink,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'OpenAI-compatible endpoints used by your agents.',
                        style: TextStyle(fontSize: 13, color: colors.ink2),
                      ),
                    ],
                  ),
                ),
                LoomButton(
                  key: const ValueKey('settings-add-connection-btn'),
                  label: 'Add connection',
                  compact: true,
                  icon: const Icon(Icons.add, size: 16),
                  onPressed: () => _showAddDialog(context, ref),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // ── Connections list ───────────────────────────────────────────
            connectionsAsync.when(
              loading: () => const SkeletonListLoader(itemCount: 3, height: 72),
              error: (e, _) => EmptyState(
                key: const ValueKey('settings-error-state'),
                icon: Icons.error_outline_rounded,
                headline: 'Failed to load connections',
                body: e.toString(),
                actionLabel: 'Retry',
                onAction: () => ref.invalidate(llmConnectionsProvider),
                colors: colors,
              ),
              data: (connections) {
                if (connections.isEmpty) {
                  return EmptyState(
                    key: const ValueKey('settings-empty-state'),
                    icon: Icons.cable_rounded,
                    headline: 'No model connections',
                    body:
                        'Add an OpenAI-compatible endpoint to power your agents.',
                    actionLabel: 'Add connection',
                    onAction: () => _showAddDialog(context, ref),
                    colors: colors,
                  );
                }
                return Column(
                  children: connections
                      .map((c) => _ConnectionRow(
                            key: ValueKey('conn-row-${c.id}'),
                            connection: c,
                            onSetDefault: () => _setDefault(context, ref, c),
                            onDelete: () =>
                                _delete(context, ref, c, connections),
                          ))
                      .toList(),
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showAddDialog(BuildContext context, WidgetRef ref) async {
    await showDialog<void>(
      context: context,
      builder: (_) => _AddConnectionDialog(
        onCreated: () {
          ref.invalidate(llmConnectionsProvider);
        },
      ),
    );
  }

  Future<void> _setDefault(
      BuildContext context, WidgetRef ref, LlmConnection c) async {
    try {
      await ref.read(llmConnectionsProvider.notifier).setDefault(c.id);
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to set default: $e')),
        );
      }
    }
  }

  Future<void> _delete(BuildContext context, WidgetRef ref, LlmConnection c,
      List<LlmConnection> all) async {
    final isLast = all.length == 1;
    final confirmed = await ConfirmDialog.show(
      context,
      title: 'Delete connection?',
      message: isLast
          ? 'This is the last connection. Agents will fall back to the mock provider until a new one is added.'
          : 'Deleting "${c.name}" cannot be undone.',
      confirmLabel: 'Delete',
      isDestructive: true,
    );
    if (!confirmed) return;
    try {
      await ref.read(llmConnectionsProvider.notifier).delete(c.id);
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to delete: $e')),
        );
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Single connection row
// ---------------------------------------------------------------------------

class _ConnectionRow extends StatelessWidget {
  final LlmConnection connection;
  final VoidCallback onSetDefault;
  final VoidCallback onDelete;

  const _ConnectionRow({
    super.key,
    required this.connection,
    required this.onSetDefault,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);
    final host = Uri.tryParse(connection.baseUrl)?.host ?? connection.baseUrl;

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(LoomRadius.card),
        border: Border.all(color: colors.line),
      ),
      child: Row(
        children: [
          // Info
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      connection.name,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: colors.ink,
                      ),
                    ),
                    if (connection.isDefault) ...[
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 7, vertical: 2),
                        decoration: BoxDecoration(
                          color: colors.accentTint,
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          'Default',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: colors.accentInk,
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 2),
                Text(
                  '$host · ${connection.model} · ${connection.apiKey}',
                  style: TextStyle(fontSize: 12, color: colors.ink3),
                ),
              ],
            ),
          ),

          // Actions
          if (!connection.isDefault)
            _ActionButton(
              label: 'Set default',
              onTap: onSetDefault,
              colors: colors,
            ),
          const SizedBox(width: 6),
          _ActionButton(
            key: ValueKey('conn-delete-${connection.id}'),
            label: 'Delete',
            onTap: onDelete,
            colors: colors,
            isDestructive: true,
          ),
        ],
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  final String label;
  final VoidCallback onTap;
  final LoomColors colors;
  final bool isDestructive;

  const _ActionButton({
    super.key,
    required this.label,
    required this.onTap,
    required this.colors,
    this.isDestructive = false,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          border: Border.all(
              color: isDestructive ? colors.danger : colors.lineStrong),
          borderRadius: BorderRadius.circular(LoomRadius.control),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12.5,
            color: isDestructive ? colors.danger : colors.ink2,
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Add connection dialog (wraps LlmConnectionForm)
// ---------------------------------------------------------------------------

class _AddConnectionDialog extends ConsumerStatefulWidget {
  final VoidCallback? onCreated;

  const _AddConnectionDialog({this.onCreated});

  @override
  ConsumerState<_AddConnectionDialog> createState() =>
      _AddConnectionDialogState();
}

class _AddConnectionDialogState extends ConsumerState<_AddConnectionDialog> {
  bool _saving = false;

  Future<void> _handleSubmit(LlmConnectionDraft draft) async {
    setState(() => _saving = true);
    try {
      await ref.read(llmConnectionsProvider.notifier).create(
            name: draft.name,
            baseUrl: draft.baseUrl,
            model: draft.model,
            apiKey: draft.apiKey,
            isDefault: draft.isDefault,
          );
      widget.onCreated?.call();
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      if (mounted) {
        setState(() => _saving = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to add connection: $e')),
        );
      }
    }
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
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 440),
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      'Add model connection',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: colors.ink,
                      ),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, size: 18),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              LlmConnectionForm(
                key: const ValueKey('settings-llm-form'),
                onSubmit: _handleSubmit,
                submitLabel: 'Add connection',
                submitting: _saving,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
