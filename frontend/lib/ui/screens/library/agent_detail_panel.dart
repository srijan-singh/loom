import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:loom_ui/models/agent_definition.dart';
import 'package:loom_ui/models/mcp_connection.dart';
import 'package:loom_ui/models/skill.dart';
import 'package:loom_ui/providers/library_providers.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Inline panel or full-screen editor for creating and updating an [AgentDefinition].
class AgentDetailPanel extends ConsumerStatefulWidget {
  final AgentDefinition? agent;
  final VoidCallback? onClose;
  final void Function(AgentDefinition saved)? onSaved;
  final VoidCallback? onCancel;

  const AgentDetailPanel({
    super.key,
    this.agent,
    this.onClose,
    this.onSaved,
    this.onCancel,
  });

  @override
  ConsumerState<AgentDetailPanel> createState() => _AgentDetailPanelState();
}

class _AgentDetailPanelState extends ConsumerState<AgentDetailPanel> {
  late final TextEditingController _nameCtrl;
  late final TextEditingController _roleCtrl;
  String? _selectedSkillId;
  late Set<String> _selectedMcpIds;
  String? _nameError;
  bool _saving = false;

  bool get _isNew => widget.agent == null;

  @override
  void initState() {
    super.initState();
    _nameCtrl = TextEditingController(text: widget.agent?.name ?? '');
    _roleCtrl =
        TextEditingController(text: widget.agent?.roleDescription ?? '');
    _selectedSkillId = widget.agent?.skillId;
    _selectedMcpIds = Set<String>.from(widget.agent?.allowedMcpIds ?? []);
  }

  @override
  void didUpdateWidget(covariant AgentDetailPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.agent?.id != widget.agent?.id) {
      _nameCtrl.text = widget.agent?.name ?? '';
      _roleCtrl.text = widget.agent?.roleDescription ?? '';
      _selectedSkillId = widget.agent?.skillId;
      _selectedMcpIds = Set<String>.from(widget.agent?.allowedMcpIds ?? []);
      _nameError = null;
    }
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _roleCtrl.dispose();
    super.dispose();
  }

  void _cancel() {
    if (widget.onCancel != null) {
      widget.onCancel!();
    } else if (widget.onClose != null) {
      widget.onClose!();
    } else if (Navigator.of(context).canPop()) {
      Navigator.of(context).pop();
    }
  }

  Future<void> _save() async {
    final name = _nameCtrl.text.trim();
    if (name.isEmpty) {
      setState(() {
        _nameError = 'Name is required';
      });
      return;
    }

    setState(() {
      _saving = true;
      _nameError = null;
    });

    try {
      final now = DateTime.now().millisecondsSinceEpoch;
      final AgentDefinition saved;
      if (_isNew) {
        saved = await ref.read(agentsProvider.notifier).createAgent(
              AgentDefinition(
                id: '',
                name: name,
                roleDescription: _roleCtrl.text.trim(),
                skillId: _selectedSkillId,
                allowedMcpIds: _selectedMcpIds.toList(),
                createdAt: now,
                updatedAt: now,
              ),
            );
      } else {
        final updated = widget.agent!.copyWith(
          name: name,
          roleDescription: _roleCtrl.text.trim(),
          skillId: _selectedSkillId,
          allowedMcpIds: _selectedMcpIds.toList(),
          updatedAt: now,
        );
        saved = await ref
            .read(agentsProvider.notifier)
            .updateAgent(widget.agent!.id, updated);
      }

      if (!mounted) return;
      setState(() => _saving = false);

      if (widget.onSaved != null) {
        widget.onSaved!(saved);
      } else if (widget.onClose != null) {
        widget.onClose!();
      } else if (Navigator.of(context).canPop()) {
        Navigator.of(context).pop(saved);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _saving = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Save failed: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);
    final skillsAsync = ref.watch(skillsProvider);
    final mcpsAsync = ref.watch(mcpsProvider);

    final skills = skillsAsync.valueOrNull ?? const <Skill>[];
    final mcps = mcpsAsync.valueOrNull ?? const <MCPConnection>[];

    final hasSkill = skills.any((s) => s.id == _selectedSkillId);
    final currentSkillValue = hasSkill ? _selectedSkillId : null;

    final headerTitle = _isNew
        ? 'New Agent'
        : (_nameCtrl.text.isNotEmpty ? _nameCtrl.text : 'Edit Agent');

    return Container(
      color: colors.surface,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // ── Header ────────────────────────────────────────────────────────
          Container(
            padding: const EdgeInsets.symmetric(
              horizontal: LoomSpace.s16,
              vertical: LoomSpace.s12,
            ),
            decoration: BoxDecoration(
              border: Border(bottom: BorderSide(color: colors.line)),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    headerTitle,
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: colors.ink,
                    ),
                  ),
                ),
                IconButton(
                  key: const ValueKey('agent-close-btn'),
                  icon: const Icon(Icons.close, size: 18),
                  tooltip: 'Close',
                  onPressed: _cancel,
                ),
              ],
            ),
          ),

          // ── Form content ──────────────────────────────────────────────────
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(LoomSpace.s16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Name (required)
                  _label('Name', colors, isRequired: true),
                  const SizedBox(height: LoomSpace.s4),
                  TextField(
                    key: const ValueKey('agent-name-field'),
                    controller: _nameCtrl,
                    style: TextStyle(fontSize: 14, color: colors.ink),
                    decoration: InputDecoration(
                      hintText: 'Agent name',
                      hintStyle:
                          TextStyle(fontSize: 13, color: colors.ink3),
                      errorText: _nameError,
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: LoomSpace.s12,
                        vertical: LoomSpace.s12,
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
                      errorBorder: OutlineInputBorder(
                        borderRadius:
                            BorderRadius.circular(LoomRadius.control),
                        borderSide: BorderSide(color: colors.danger),
                      ),
                      focusedErrorBorder: OutlineInputBorder(
                        borderRadius:
                            BorderRadius.circular(LoomRadius.control),
                        borderSide: BorderSide(color: colors.danger),
                      ),
                    ),
                    onChanged: (v) {
                      if (_nameError != null && v.trim().isNotEmpty) {
                        setState(() => _nameError = null);
                      }
                    },
                  ),
                  const SizedBox(height: LoomSpace.s16),

                  // Role (multiline)
                  _label('Role Description', colors),
                  const SizedBox(height: LoomSpace.s4),
                  TextField(
                    key: const ValueKey('agent-role-field'),
                    controller: _roleCtrl,
                    maxLines: 3,
                    minLines: 2,
                    style: TextStyle(fontSize: 14, color: colors.ink),
                    decoration: InputDecoration(
                      hintText: 'Describe what this agent does…',
                      hintStyle:
                          TextStyle(fontSize: 13, color: colors.ink3),
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: LoomSpace.s12,
                        vertical: LoomSpace.s12,
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
                  ),
                  const SizedBox(height: LoomSpace.s16),

                  // Skill Dropdown
                  _label('Skill', colors),
                  const SizedBox(height: LoomSpace.s4),
                  DropdownButtonFormField<String?>(
                    key: const ValueKey('agent-skill-dropdown'),
                    initialValue: currentSkillValue,
                    style: TextStyle(fontSize: 14, color: colors.ink),
                    dropdownColor: colors.surface,
                    decoration: InputDecoration(
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: LoomSpace.s12,
                        vertical: LoomSpace.s8,
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
                    items: [
                      DropdownMenuItem<String?>(
                        value: null,
                        child: Text('None',
                            style: TextStyle(color: colors.ink)),
                      ),
                      ...skills.map(
                        (s) => DropdownMenuItem<String?>(
                          value: s.id,
                          child: Text(s.name,
                              style: TextStyle(color: colors.ink)),
                        ),
                      ),
                    ],
                    onChanged: (val) =>
                        setState(() => _selectedSkillId = val),
                  ),
                  const SizedBox(height: LoomSpace.s16),

                  // Allowed MCPs
                  _label('Allowed MCPs', colors),
                  const SizedBox(height: LoomSpace.s4),
                  if (mcps.isEmpty)
                    Padding(
                      padding:
                          const EdgeInsets.symmetric(vertical: LoomSpace.s8),
                      child: Text(
                        'No MCP connections available.',
                        style: TextStyle(fontSize: 13, color: colors.ink3),
                      ),
                    )
                  else
                    Material(
                      color: colors.sunken,
                      shape: RoundedRectangleBorder(
                        borderRadius:
                            BorderRadius.circular(LoomRadius.control),
                        side: BorderSide(color: colors.line),
                      ),
                      child: Column(
                        children: mcps.map((mcp) {
                          final isChecked =
                              _selectedMcpIds.contains(mcp.id);
                          return CheckboxListTile(
                            key: ValueKey('agent-mcp-${mcp.id}'),
                            value: isChecked,
                            title: Text(
                              mcp.name,
                              style: TextStyle(
                                  fontSize: 13, color: colors.ink),
                            ),
                            subtitle: mcp.detail != null &&
                                    mcp.detail!.isNotEmpty
                                ? Text(mcp.detail!,
                                    style: TextStyle(
                                        fontSize: 12, color: colors.ink3))
                                : null,
                            activeColor: colors.accent,
                            checkColor: colors.onAccent,
                            dense: true,
                            controlAffinity:
                                ListTileControlAffinity.leading,
                            onChanged: (checked) {
                              setState(() {
                                if (checked == true) {
                                  _selectedMcpIds.add(mcp.id);
                                } else {
                                  _selectedMcpIds.remove(mcp.id);
                                }
                              });
                            },
                          );
                        }).toList(),
                      ),
                    ),
                ],
              ),
            ),
          ),

          // ── Bottom Action Bar ─────────────────────────────────────────────
          Container(
            padding: const EdgeInsets.all(LoomSpace.s16),
            decoration: BoxDecoration(
              border: Border(top: BorderSide(color: colors.line)),
              color: colors.surface,
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                OutlinedButton(
                  key: const ValueKey('agent-cancel-btn'),
                  onPressed: _saving ? null : _cancel,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: colors.ink2,
                    side: BorderSide(color: colors.lineStrong),
                    padding: const EdgeInsets.symmetric(
                      horizontal: LoomSpace.s16,
                      vertical: LoomSpace.s8,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius:
                          BorderRadius.circular(LoomRadius.control),
                    ),
                  ),
                  child: const Text('Cancel'),
                ),
                const SizedBox(width: LoomSpace.s8),
                FilledButton(
                  key: const ValueKey('agent-save-btn'),
                  onPressed: _saving ? null : _save,
                  style: FilledButton.styleFrom(
                    backgroundColor: colors.accent,
                    foregroundColor: colors.onAccent,
                    padding: const EdgeInsets.symmetric(
                      horizontal: LoomSpace.s16,
                      vertical: LoomSpace.s8,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius:
                          BorderRadius.circular(LoomRadius.control),
                    ),
                  ),
                  child: _saving
                      ? SizedBox(
                          width: 14,
                          height: 14,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: colors.onAccent,
                          ),
                        )
                      : const Text('Save'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _label(String text, LoomColors colors, {bool isRequired = false}) {
    return Row(
      children: [
        Text(
          text,
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: colors.ink2,
            letterSpacing: 0.3,
          ),
        ),
        if (isRequired)
          Text(
            ' *',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: colors.danger,
            ),
          ),
      ],
    );
  }
}
