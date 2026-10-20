import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:loom_ui/models/agent_definition.dart';
import 'package:loom_ui/models/mcp_connection.dart';
import 'package:loom_ui/models/skill.dart';
import 'package:loom_ui/providers/library_providers.dart';
import 'package:loom_ui/ui/components/agent_card.dart';
import 'package:loom_ui/ui/components/confirm_dialog.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/components/skeleton_loader.dart';
import 'package:loom_ui/ui/screens/library/agent_detail_panel.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Top-level view for Agent Definitions Tab (`/library/agents`).
///
/// Features a responsive two-column layout on wide screens (>= 800px) and
/// full-screen navigation fallback on narrow screens (< 800px).
class AgentsTabView extends ConsumerStatefulWidget {
  final void Function(AgentDefinition agent)? onSelectAgent;
  final VoidCallback? onNewAgent;

  const AgentsTabView({
    super.key,
    this.onSelectAgent,
    this.onNewAgent,
  });

  @override
  ConsumerState<AgentsTabView> createState() => _AgentsTabViewState();
}

class _AgentsTabViewState extends ConsumerState<AgentsTabView> {
  String? _selectedAgentId;
  bool _isCreatingNew = false;

  void _onAgentTap(BuildContext context, AgentDefinition agent, bool isWide) {
    widget.onSelectAgent?.call(agent);
    if (isWide) {
      setState(() {
        _selectedAgentId = agent.id;
        _isCreatingNew = false;
      });
    } else {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => Scaffold(
            backgroundColor: LoomColors.of(context).bg,
            body: SafeArea(
              child: AgentDetailPanel(
                agent: agent,
                onSaved: (saved) => Navigator.of(context).pop(),
                onClose: () => Navigator.of(context).pop(),
              ),
            ),
          ),
        ),
      );
    }
  }

  void _onNewAgent(BuildContext context, bool isWide) {
    widget.onNewAgent?.call();
    if (isWide) {
      setState(() {
        _isCreatingNew = true;
        _selectedAgentId = null;
      });
    } else {
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => Scaffold(
            backgroundColor: LoomColors.of(context).bg,
            body: SafeArea(
              child: AgentDetailPanel(
                onSaved: (saved) => Navigator.of(context).pop(),
                onClose: () => Navigator.of(context).pop(),
              ),
            ),
          ),
        ),
      );
    }
  }

  Future<void> _confirmDelete(BuildContext context, String id) async {
    final messenger = ScaffoldMessenger.of(context);
    final confirmed = await ConfirmDialog.show(
      context,
      title: 'Delete agent?',
      message: 'This action cannot be undone.',
      confirmLabel: 'Delete',
      isDestructive: true,
    );
    if (confirmed) {
      try {
        await ref.read(agentsProvider.notifier).deleteAgent(id);
      } catch (e) {
        if (mounted) {
          messenger.showSnackBar(
            SnackBar(content: Text('Delete failed: $e')),
          );
        }
        return;
      }
      if (_selectedAgentId == id && mounted) {
        setState(() {
          _selectedAgentId = null;
        });
      }
    }
  }

  String? _resolveSkillName(String? skillId, List<Skill> skills) {
    if (skillId == null) return null;
    for (final s in skills) {
      if (s.id == skillId) return s.name;
    }
    return null;
  }

  List<String> _resolveMcpNames(
    List<String> allowedIds,
    List<MCPConnection> mcps,
  ) {
    final names = <String>[];
    for (final id in allowedIds) {
      for (final mcp in mcps) {
        if (mcp.id == id) {
          names.add(mcp.name);
          break;
        }
      }
    }
    return names;
  }

  Widget _buildRightPanel(LoomColors colors, List<AgentDefinition> agents) {
    if (_isCreatingNew) {
      return AgentDetailPanel(
        key: const ValueKey('agent-detail-panel-new'),
        onClose: () => setState(() => _isCreatingNew = false),
        onSaved: (saved) {
          setState(() {
            _isCreatingNew = false;
            _selectedAgentId = saved.id;
          });
        },
      );
    }

    if (_selectedAgentId != null) {
      AgentDefinition? agent;
      for (final a in agents) {
        if (a.id == _selectedAgentId) {
          agent = a;
          break;
        }
      }

      if (agent != null) {
        return AgentDetailPanel(
          key: ValueKey('agent-detail-panel-${agent.id}'),
          agent: agent,
          onClose: () => setState(() => _selectedAgentId = null),
          onSaved: (saved) {
            // Saving an existing agent closes the inline editor.
            setState(() {
              _selectedAgentId = null;
            });
          },
        );
      }
    }

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(LoomSpace.s24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.smart_toy_outlined, size: 48, color: colors.ink3),
            const SizedBox(height: LoomSpace.s16),
            Text(
              'Select an agent to view details or create a new one.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 14, color: colors.ink2),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);
    final agentsAsync = ref.watch(agentsProvider);
    final skillsAsync = ref.watch(skillsProvider);
    final mcpsAsync = ref.watch(mcpsProvider);

    final skills = skillsAsync.valueOrNull ?? const <Skill>[];
    final mcps = mcpsAsync.valueOrNull ?? const <MCPConnection>[];

    return LayoutBuilder(
      builder: (context, constraints) {
        final isWide = constraints.maxWidth >= 800;

        return agentsAsync.when(
          loading: () => const Padding(
            padding: EdgeInsets.all(LoomSpace.s16),
            child: SkeletonListLoader(itemCount: 3, height: 96),
          ),
          error: (e, _) => Center(
            child: Text(
              'Failed to load agents: $e',
              style: TextStyle(color: colors.danger),
            ),
          ),
          data: (agents) {
            final listWidget = Column(
              children: [
                // ── Header ──────────────────────────────────────────────────
                Padding(
                  padding: const EdgeInsets.all(LoomSpace.s16),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          'Agent Definitions',
                          style: TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.w600,
                            color: colors.ink,
                          ),
                        ),
                      ),
                      FilledButton.icon(
                        key: const ValueKey('new-agent-btn'),
                        onPressed: () => _onNewAgent(context, isWide),
                        icon: const Icon(Icons.add, size: 16),
                        label: const Text('New Agent'),
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

                // ── List / EmptyState ───────────────────────────────────────
                Expanded(
                  child: agents.isEmpty
                      ? Padding(
                          padding: const EdgeInsets.all(LoomSpace.s16),
                          child: EmptyState(
                            icon: Icons.smart_toy_outlined,
                            headline: 'No agents yet',
                            body: 'Create your first agent definition.',
                            actionLabel: 'New Agent',
                            onAction: () => _onNewAgent(context, isWide),
                          ),
                        )
                      : RefreshIndicator(
                          onRefresh: () => ref.refresh(agentsProvider.future),
                          child: ListView.separated(
                            padding: const EdgeInsets.fromLTRB(
                              LoomSpace.s16,
                              0,
                              LoomSpace.s16,
                              LoomSpace.s16,
                            ),
                            itemCount: agents.length,
                            separatorBuilder: (_, __) =>
                                const SizedBox(height: LoomSpace.s8),
                            itemBuilder: (ctx, i) {
                              final agent = agents[i];
                              final skillName =
                                  _resolveSkillName(agent.skillId, skills);
                              final mcpNames = _resolveMcpNames(
                                agent.allowedMcpIds,
                                mcps,
                              );

                              return AgentCard(
                                key: ValueKey('agent-card-${agent.id}'),
                                agent: agent,
                                skillName: skillName,
                                mcpNames: mcpNames,
                                isSelected: isWide &&
                                    _selectedAgentId == agent.id &&
                                    !_isCreatingNew,
                                onTap: () =>
                                    _onAgentTap(context, agent, isWide),
                                onEdit: () =>
                                    _onAgentTap(context, agent, isWide),
                                onDelete: () =>
                                    _confirmDelete(context, agent.id),
                              );
                            },
                          ),
                        ),
                ),
              ],
            );

            if (!isWide) {
              return listWidget;
            }

            return Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(
                  flex: 5,
                  child: listWidget,
                ),
                VerticalDivider(
                  width: 1,
                  thickness: 1,
                  color: colors.line,
                ),
                Expanded(
                  flex: 5,
                  child: _buildRightPanel(colors, agents),
                ),
              ],
            );
          },
        );
      },
    );
  }
}
