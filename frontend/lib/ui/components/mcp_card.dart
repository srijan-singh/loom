import 'package:flutter/material.dart';
import 'package:loom_ui/models/mcp_connection.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// MCP list item: status dot (green = CONNECTED, grey = DISCONNECTED), name,
/// type badge, tools summary, and overflow menu [Disconnect, Delete].
class MCPCard extends StatelessWidget {
  final MCPConnection mcp;
  final VoidCallback? onDisconnect;
  final VoidCallback onDelete;

  const MCPCard({
    super.key,
    required this.mcp,
    this.onDisconnect,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);
    final isConnected = mcp.status == MCPStatus.CONNECTED;
    final dotColor = isConnected ? colors.success : colors.ink3;

    return Container(
      padding: const EdgeInsets.all(LoomSpace.s16),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(LoomRadius.card),
        border: Border.all(color: colors.line),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Status dot
          Padding(
            padding: const EdgeInsets.only(top: 3),
            child: Container(
              key: const ValueKey('mcp-status-dot'),
              width: 8,
              height: 8,
              decoration: BoxDecoration(
                color: dotColor,
                shape: BoxShape.circle,
              ),
            ),
          ),
          const SizedBox(width: LoomSpace.s12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        mcp.name,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: colors.ink,
                        ),
                      ),
                    ),
                    // Type badge
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: colors.sunken,
                        borderRadius:
                            BorderRadius.circular(LoomRadius.control),
                        border: Border.all(color: colors.line),
                      ),
                      child: Text(
                        mcp.type,
                        style: TextStyle(fontSize: 11, color: colors.ink3),
                      ),
                    ),
                  ],
                ),
                if (mcp.detail != null && mcp.detail!.isNotEmpty) ...[
                  const SizedBox(height: LoomSpace.s4),
                  Text(
                    mcp.detail!,
                    style: TextStyle(fontSize: 12, color: colors.ink3),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
                if (mcp.tools.isNotEmpty) ...[
                  const SizedBox(height: LoomSpace.s4),
                  Text(
                    '${mcp.tools.length} tool${mcp.tools.length == 1 ? '' : 's'}',
                    style: TextStyle(fontSize: 12, color: colors.ink3),
                  ),
                ],
              ],
            ),
          ),
          _OverflowMenu(
            items: [
              if (onDisconnect != null)
                _MenuItem(
                  key: const ValueKey('mcp-disconnect'),
                  label: 'Disconnect',
                  icon: Icons.link_off_outlined,
                  onTap: onDisconnect!,
                ),
              _MenuItem(
                key: const ValueKey('mcp-delete'),
                label: 'Delete',
                icon: Icons.delete_outline,
                onTap: onDelete,
                isDestructive: true,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Shared overflow menu helpers (duplicated to keep files self-contained)
// ---------------------------------------------------------------------------

class _MenuItem {
  final Key? key;
  final String label;
  final IconData icon;
  final VoidCallback onTap;
  final bool isDestructive;

  const _MenuItem({
    this.key,
    required this.label,
    required this.icon,
    required this.onTap,
    this.isDestructive = false,
  });
}

class _OverflowMenu extends StatelessWidget {
  final List<_MenuItem> items;

  const _OverflowMenu({required this.items});

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);

    return PopupMenuButton<int>(
      icon: Icon(Icons.more_vert, size: 18, color: colors.ink3),
      padding: EdgeInsets.zero,
      itemBuilder: (_) => [
        for (int i = 0; i < items.length; i++)
          PopupMenuItem<int>(
            key: items[i].key,
            value: i,
            child: Row(
              children: [
                Icon(
                  items[i].icon,
                  size: 16,
                  color: items[i].isDestructive ? colors.danger : colors.ink2,
                ),
                const SizedBox(width: LoomSpace.s8),
                Text(
                  items[i].label,
                  style: TextStyle(
                    fontSize: 13,
                    color:
                        items[i].isDestructive ? colors.danger : colors.ink,
                  ),
                ),
              ],
            ),
          ),
      ],
      onSelected: (i) => items[i].onTap(),
    );
  }
}
