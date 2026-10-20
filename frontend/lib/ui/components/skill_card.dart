import 'package:flutter/material.dart';
import 'package:loom_ui/models/skill.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Skill list item: name (bold), 2-line truncated description, tag chips, and
/// an overflow menu with [Edit] and [Delete] actions.
class SkillCard extends StatelessWidget {
  final Skill skill;
  final VoidCallback onTap;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  const SkillCard({
    super.key,
    required this.skill,
    required this.onTap,
    required this.onEdit,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);

    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(LoomSpace.s16),
        decoration: BoxDecoration(
          color: colors.surface,
          borderRadius: BorderRadius.circular(LoomRadius.card),
          border: Border.all(color: colors.line),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    skill.name,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: colors.ink,
                    ),
                  ),
                  if (skill.description.isNotEmpty) ...[
                    const SizedBox(height: LoomSpace.s4),
                    Text(
                      skill.description,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(fontSize: 13, color: colors.ink2),
                    ),
                  ],
                  if (skill.tags.isNotEmpty) ...[
                    const SizedBox(height: LoomSpace.s8),
                    Wrap(
                      spacing: LoomSpace.s4,
                      runSpacing: LoomSpace.s4,
                      children: skill.tags
                          .map(
                            (tag) => Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 6,
                                vertical: 2,
                              ),
                              decoration: BoxDecoration(
                                color: colors.accentTint,
                                borderRadius:
                                    BorderRadius.circular(LoomRadius.control),
                              ),
                              child: Text(
                                tag,
                                style: TextStyle(
                                  fontSize: 11,
                                  color: colors.accentInk,
                                ),
                              ),
                            ),
                          )
                          .toList(),
                    ),
                  ],
                ],
              ),
            ),
            _OverflowMenu(
              items: [
                _MenuItem(
                  key: const ValueKey('skill-edit'),
                  label: 'Edit',
                  icon: Icons.edit_outlined,
                  onTap: onEdit,
                ),
                _MenuItem(
                  key: const ValueKey('skill-delete'),
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
