import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:loom_ui/providers/library_providers.dart';
import 'package:loom_ui/ui/components/confirm_dialog.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/components/skill_card.dart';
import 'package:loom_ui/ui/components/skeleton_loader.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Top-level view for the Skills tab (`/library/skills`).
///
/// [onNewSkill] and [onOpenSkill] are injectable for testing; they default to
/// go_router navigation so production code works without passing them.
class SkillsTabView extends ConsumerStatefulWidget {
  final VoidCallback? onNewSkill;
  final void Function(String id)? onOpenSkill;

  const SkillsTabView({
    super.key,
    this.onNewSkill,
    this.onOpenSkill,
  });

  @override
  ConsumerState<SkillsTabView> createState() => _SkillsTabViewState();
}

class _SkillsTabViewState extends ConsumerState<SkillsTabView> {
  final _searchCtrl = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  void _navigateNew() {
    if (widget.onNewSkill != null) {
      widget.onNewSkill!();
    } else {
      context.push('/library/skills/new');
    }
  }

  void _navigateEdit(String id) {
    if (widget.onOpenSkill != null) {
      widget.onOpenSkill!(id);
    } else {
      context.push('/library/skills/$id');
    }
  }

  Future<void> _confirmDelete(BuildContext ctx, String id) async {
    final confirmed = await ConfirmDialog.show(
      ctx,
      title: 'Delete skill?',
      message: 'This action cannot be undone.',
      confirmLabel: 'Delete',
      isDestructive: true,
    );
    if (!confirmed) return;
    try {
      await ref.read(skillsProvider.notifier).deleteSkill(id);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Delete failed: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);
    final skillsAsync = ref.watch(skillsProvider);

    return Column(
      children: [
        // ── Search + New Skill row ────────────────────────────────────────
        Padding(
          padding: const EdgeInsets.all(LoomSpace.s16),
          child: Row(
            children: [
              Expanded(
                child: TextField(
                  key: const ValueKey('skills-search'),
                  controller: _searchCtrl,
                  style: TextStyle(fontSize: 13, color: colors.ink),
                  decoration: InputDecoration(
                    hintText: 'Search skills…',
                    hintStyle: TextStyle(fontSize: 13, color: colors.ink3),
                    prefixIcon: Icon(Icons.search, size: 18, color: colors.ink3),
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: LoomSpace.s12,
                      vertical: 0,
                    ),
                    isDense: true,
                    filled: true,
                    fillColor: colors.sunken,
                    border: OutlineInputBorder(
                      borderRadius:
                          BorderRadius.circular(LoomRadius.control),
                      borderSide: BorderSide(color: colors.line),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius:
                          BorderRadius.circular(LoomRadius.control),
                      borderSide: BorderSide(color: colors.line),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius:
                          BorderRadius.circular(LoomRadius.control),
                      borderSide: BorderSide(color: colors.accent),
                    ),
                  ),
                  onChanged: (v) => setState(() => _query = v.toLowerCase()),
                ),
              ),
              const SizedBox(width: LoomSpace.s12),
              FilledButton.icon(
                key: const ValueKey('new-skill-btn'),
                onPressed: _navigateNew,
                icon: const Icon(Icons.add, size: 16),
                label: const Text('New Skill'),
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
                    borderRadius:
                        BorderRadius.circular(LoomRadius.control),
                  ),
                ),
              ),
            ],
          ),
        ),

        // ── List / skeleton / empty ──────────────────────────────────────
        Expanded(
          child: skillsAsync.when(
            loading: () => const Padding(
              padding: EdgeInsets.all(LoomSpace.s16),
              child: SkeletonListLoader(itemCount: 3, height: 96),
            ),
            error: (e, _) => Center(
              child: Text(
                'Failed to load skills: $e',
                style: TextStyle(color: LoomColors.of(context).danger),
              ),
            ),
            data: (skills) {
              final filtered = _query.isEmpty
                  ? skills
                  : skills
                      .where((s) =>
                          s.name.toLowerCase().contains(_query) ||
                          s.tags.any((t) => t.toLowerCase().contains(_query)))
                      .toList();

              if (skills.isEmpty) {
                return Padding(
                  padding: const EdgeInsets.all(LoomSpace.s16),
                  child: EmptyState(
                    icon: Icons.extension_outlined,
                    headline: 'No skills yet',
                    body: 'Create your first skill.',
                    actionLabel: 'New Skill',
                    onAction: _navigateNew,
                  ),
                );
              }

              if (filtered.isEmpty) {
                return Padding(
                  padding: const EdgeInsets.all(LoomSpace.s16),
                  child: EmptyState(
                    icon: Icons.search_off,
                    headline: 'No matching skills',
                    body: 'Try a different search.',
                    actionLabel: 'Clear search',
                    onAction: () {
                      _searchCtrl.clear();
                      setState(() => _query = '');
                    },
                  ),
                );
              }

              return RefreshIndicator(
                onRefresh: () => ref.refresh(skillsProvider.future),
                child: ListView.separated(
                  padding: const EdgeInsets.fromLTRB(
                    LoomSpace.s16,
                    0,
                    LoomSpace.s16,
                    LoomSpace.s16,
                  ),
                  itemCount: filtered.length,
                  separatorBuilder: (_, __) =>
                      const SizedBox(height: LoomSpace.s8),
                  itemBuilder: (ctx, i) {
                    final skill = filtered[i];
                    return SkillCard(
                      key: ValueKey('skill-card-${skill.id}'),
                      skill: skill,
                      onTap: () => _navigateEdit(skill.id),
                      onEdit: () => _navigateEdit(skill.id),
                      onDelete: () => _confirmDelete(ctx, skill.id),
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
