import 'package:flutter/material.dart';
import 'package:loom_ui/models/agent_definition.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Agent list item: name (bold), role description (2-line truncated),
/// skill name badge, MCP names summary, and overflow menu [Edit, Delete].
class AgentCard extends StatelessWidget {
  final AgentDefinition agent;
  final String? skillName;
  final List<String> mcpNames;
  final bool isSelected;
  final VoidCallback onTap;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  const AgentCard({
    super.key,
    required this.agent,
    this.skillName,
    this.mcpNames = const [],
    this.isSelected = false,
    required this.onTap,
    required this.onEdit,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);
    final borderColor = isSelected ? colors.accent : colors.line;

    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(LoomSpace.s16),
        decoration: BoxDecoration(
          color: isSelected ? colors.accentTint : colors.surface,
          borderRadius: BorderRadius.circular(LoomRadius.card),
          border: Border.all(color: borderColor),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    agent.name,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: colors.ink,
                    ),
                  ),
                  if (agent.roleDescription.isNotEmpty) ...[
                    const SizedBox(height: LoomSpace.s4),
                    Text(
                      agent.roleDescription,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(fontSize: 13, color: colors.ink2),
                    ),
                  ],
                  const SizedBox(height: LoomSpace.s8),
                  Wrap(
                    spacing: LoomSpace.s4,
                    runSpacing: LoomSpace.s4,
                    children: [
                      // Skill badge
                      if (skillName != null)
                        _Badge(
                          label: skillName!,
                          icon: Icons.bolt_outlined,
                          colors: colors,
                        ),
                      // MCP names
                      for (final mcp in mcpNames)
                        _Badge(
                          label: mcp,
                          icon: Icons.cable_outlined,
                          colors: colors,
                        ),
                    ],
                  ),
                ],
              ),
            ),
            _OverflowMenu(
              items: [
                _MenuItem(
                  key: const ValueKey('agent-edit'),
                  label: 'Edit',
                  icon: Icons.edit_outlined,
                  onTap: onEdit,
                ),
                _MenuItem(
                  key: const ValueKey('agent-delete'),
                  label: 'Delete',
                  icon: Icons.delete_outline,
                  onTap: onDelete,
                  isDestructive: true,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  final String label;
  final IconData icon;
  final LoomColors colors;

  const _Badge({
    required this.label,
    required this.icon,
    required this.colors,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: colors.sunken,
        borderRadius: BorderRadius.circular(LoomRadius.control),
        border: Border.all(color: colors.line),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 11, color: colors.ink3),
          const SizedBox(width: 3),
          Text(
            label,
            style: TextStyle(fontSize: 11, color: colors.ink2),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Shared overflow menu helpers
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
