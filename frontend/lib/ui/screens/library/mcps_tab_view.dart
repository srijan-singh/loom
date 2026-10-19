import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:loom_ui/models/mcp_connection.dart';
import 'package:loom_ui/providers/library_providers.dart';
import 'package:loom_ui/ui/components/confirm_dialog.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/components/mcp_card.dart';
import 'package:loom_ui/ui/components/skeleton_loader.dart';
import 'package:loom_ui/ui/screens/library/add_mcp_bottom_sheet.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Top-level view for the MCP Connections tab (`/library/mcps`).
class McpsTabView extends ConsumerWidget {
  const McpsTabView({super.key});

  Future<void> _confirmDelete(
      BuildContext context, WidgetRef ref, MCPConnection mcp) async {
    final confirmed = await ConfirmDialog.show(
      context,
      title: 'Delete connection?',
      message: 'This action cannot be undone.',
      confirmLabel: 'Delete',
      isDestructive: true,
    );
    if (confirmed) {
      await ref.read(mcpsProvider.notifier).deleteMcp(mcp.id);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colors = LoomColors.of(context);
    final mcpsAsync = ref.watch(mcpsProvider);

    void openAddSheet() => AddMCPBottomSheet.show(context);

    return Column(
      children: [
        // ── Header ────────────────────────────────────────────────────────
        Padding(
          padding: const EdgeInsets.all(LoomSpace.s16),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  'MCP Connections',
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: colors.ink,
                  ),
                ),
              ),
              FilledButton.icon(
                key: const ValueKey('add-mcp-btn'),
                onPressed: openAddSheet,
                icon: const Icon(Icons.add, size: 16),
                label: const Text('Add Connection'),
                style: FilledButton.styleFrom(
                  backgroundColor: colors.accent,
                  foregroundColor: colors.onAccent,
                  padding: const EdgeInsets.symmetric(
                    horizontal: LoomSpace.s16,
                    vertical: LoomSpace.s8,
                  ),
                  textStyle: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                  ),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(LoomRadius.control),
                  ),
                ),
              ),
            ],
          ),
        ),

        // ── List / skeleton / empty ──────────────────────────────────────
        Expanded(
          child: mcpsAsync.when(
            loading: () => const Padding(
              padding: EdgeInsets.all(LoomSpace.s16),
              child: SkeletonListLoader(itemCount: 3, height: 96),
            ),
            error: (e, _) => Center(
              child: Text(
                'Failed to load MCP connections: $e',
                style: TextStyle(color: colors.danger),
              ),
            ),
            data: (mcps) {
              if (mcps.isEmpty) {
                return Padding(
                  padding: const EdgeInsets.all(LoomSpace.s16),
                  child: EmptyState(
                    icon: Icons.hub_outlined,
                    headline: 'No MCP connections',
                    body: 'Add your first connection.',
                    actionLabel: 'Add Connection',
                    onAction: openAddSheet,
                  ),
                );
              }

              return RefreshIndicator(
                onRefresh: () => ref.refresh(mcpsProvider.future),
                child: ListView.separated(
                  padding: const EdgeInsets.fromLTRB(
                    LoomSpace.s16,
                    0,
                    LoomSpace.s16,
                    LoomSpace.s16,
                  ),
                  itemCount: mcps.length,
                  separatorBuilder: (_, __) =>
                      const SizedBox(height: LoomSpace.s8),
                  itemBuilder: (ctx, i) {
                    final mcp = mcps[i];
                    return MCPCard(
                      key: ValueKey('mcp-card-${mcp.id}'),
                      mcp: mcp,
                      onDisconnect: mcp.status == MCPStatus.CONNECTED
                          ? () => ref
                              .read(mcpsProvider.notifier)
                              .updateMcpStatus(mcp.id, 'DISCONNECTED')
                          : null,
                      onDelete: () => _confirmDelete(ctx, ref, mcp),
                    );
                  },
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
