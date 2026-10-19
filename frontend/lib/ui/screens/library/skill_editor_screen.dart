import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:loom_ui/models/skill.dart';
import 'package:loom_ui/providers/library_providers.dart';
import 'package:loom_ui/services/api_client.dart';
import 'package:loom_ui/ui/components/confirm_dialog.dart';
import 'package:loom_ui/ui/components/markdown_preview.dart';
import 'package:loom_ui/ui/components/tag_chip_input.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Skill Detail & Editor Screen (`/library/skills/:id` & `/library/skills/new`).
///
/// - Pass [skillId] = null for new skill creation.
/// - [onBack] is injectable for testing; defaults to [Navigator.pop].
class SkillEditorScreen extends ConsumerStatefulWidget {
  final String? skillId;
  final VoidCallback? onBack;

  const SkillEditorScreen({
    super.key,
    this.skillId,
    this.onBack,
  });

  @override
  ConsumerState<SkillEditorScreen> createState() => _SkillEditorScreenState();
}

class _SkillEditorScreenState extends ConsumerState<SkillEditorScreen> {
  // ── form controllers ────────────────────────────────────────────────────
  final _nameCtrl = TextEditingController();
  final _descCtrl = TextEditingController();
  final _contentCtrl = TextEditingController();

  // ── state ────────────────────────────────────────────────────────────────
  Skill? _original;
  List<String> _tags = [];
  bool _loading = true;
  bool _saving = false;
  bool _showPreview = false;
  String? _errorMessage;

  // ── dirty tracking ───────────────────────────────────────────────────────
  bool get _isDirty {
    if (_original == null) {
      // new skill: dirty if any field has content
      return _nameCtrl.text.isNotEmpty ||
          _descCtrl.text.isNotEmpty ||
          _contentCtrl.text.isNotEmpty ||
          _tags.isNotEmpty;
    }
    return _nameCtrl.text != _original!.name ||
        _descCtrl.text != _original!.description ||
        _contentCtrl.text != _original!.content ||
        !_listEquals(_tags, _original!.tags);
  }

  bool get _isNew => widget.skillId == null;
  bool get _canSave => _isDirty && _nameCtrl.text.isNotEmpty;

  @override
  void initState() {
    super.initState();
    _nameCtrl.addListener(() => setState(() {}));
    _descCtrl.addListener(() => setState(() {}));
    _contentCtrl.addListener(() => setState(() {}));
    _loadSkill();
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _descCtrl.dispose();
    _contentCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadSkill() async {
    if (_isNew) {
      setState(() => _loading = false);
      return;
    }

    final id = widget.skillId!;

    // Await the provider future so we get the data even if it hasn't settled
    // yet (important for tests and cold starts).
    Skill? found;
    try {
      final allSkills = await ref.read(skillsProvider.future);
      try {
        found = allSkills.firstWhere((s) => s.id == id);
      } catch (_) {
        // not in list — fall through to direct API call
      }
    } catch (_) {
      // provider failed — fall through to direct API call
    }

    // Fall back to direct API call
    if (found == null) {
      try {
        found = await ref.read(apiClientProvider).getSkill(id);
      } catch (e) {
        if (mounted) {
          setState(() {
            _errorMessage = 'Could not load skill: $e';
            _loading = false;
          });
        }
        return;
      }
    }

    if (!mounted) return;
    _original = found;
    _nameCtrl.text = found.name;
    _descCtrl.text = found.description;
    _contentCtrl.text = found.content;
    _tags = List<String>.from(found.tags);
    setState(() => _loading = false);
  }

  Future<void> _save() async {
    if (!_canSave) return;
    setState(() => _saving = true);
    try {
      final now = DateTime.now().millisecondsSinceEpoch;
      if (_isNew) {
        final created = await ref.read(skillsProvider.notifier).createSkill(
              Skill(
                id: '', // server assigns
                name: _nameCtrl.text.trim(),
                description: _descCtrl.text.trim(),
                content: _contentCtrl.text,
                tags: List<String>.from(_tags),
                createdAt: now,
                updatedAt: now,
              ),
            );
        _original = created;
      } else {
        final updated = await ref.read(skillsProvider.notifier).updateSkill(
              widget.skillId!,
              Skill(
                id: widget.skillId!,
                name: _nameCtrl.text.trim(),
                description: _descCtrl.text.trim(),
                content: _contentCtrl.text,
                tags: List<String>.from(_tags),
                createdAt: _original!.createdAt,
                updatedAt: now,
              ),
            );
        _original = updated;
      }
      if (mounted) setState(() => _saving = false);
    } catch (e) {
      if (mounted) {
        setState(() => _saving = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Save failed: $e')),
        );
      }
    }
  }

  Future<void> _delete() async {
    final confirmed = await ConfirmDialog.show(
      context,
      title: 'Delete skill?',
      message: 'This action cannot be undone.',
      confirmLabel: 'Delete',
      isDestructive: true,
    );
    if (!confirmed || !mounted) return;
    try {
      await ref.read(skillsProvider.notifier).deleteSkill(widget.skillId!);
      _navigateBack();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Delete failed: $e')),
        );
      }
    }
  }

  void _navigateBack() {
    if (widget.onBack != null) {
      widget.onBack!();
    } else {
      Navigator.of(context).pop();
    }
  }

  Future<bool> _handlePop() async {
    if (!_isDirty) return true;
    final discard = await ConfirmDialog.show(
      context,
      title: 'Discard changes?',
      message: 'Your unsaved changes will be lost.',
      confirmLabel: 'Discard',
      isDestructive: true,
    );
    return discard;
  }

  static bool _listEquals(List<String> a, List<String> b) {
    if (a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);

    return PopScope(
      canPop: !_isDirty,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        final allow = await _handlePop();
        if (allow && mounted) {
          _navigateBack();
        }
      },
      child: Scaffold(
        backgroundColor: colors.bg,
        appBar: AppBar(
          backgroundColor: colors.surface,
          elevation: 0,
          leading: IconButton(
            key: const ValueKey('editor-back-btn'),
            icon: const Icon(Icons.arrow_back),
            tooltip: '← Skills',
            onPressed: () async {
              if (!_isDirty) {
                _navigateBack();
              } else {
                final allow = await _handlePop();
                if (allow && mounted) _navigateBack();
              }
            },
          ),
          title: Text(
            _isNew
                ? 'New Skill'
                : (_nameCtrl.text.isEmpty ? 'Skill' : _nameCtrl.text),
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              color: colors.ink,
            ),
          ),
          actions: [
            // Preview toggle
            IconButton(
              key: const ValueKey('preview-toggle-btn'),
              icon: Icon(
                _showPreview
                    ? Icons.edit_outlined
                    : Icons.preview_outlined,
              ),
              tooltip: _showPreview ? 'Edit' : 'Preview',
              onPressed: () => setState(() => _showPreview = !_showPreview),
            ),

            // Save button
            Padding(
              padding: const EdgeInsets.only(right: LoomSpace.s8),
              child: FilledButton(
                key: const ValueKey('save-btn'),
                onPressed: _canSave && !_saving ? _save : null,
                style: FilledButton.styleFrom(
                  backgroundColor: colors.accent,
                  foregroundColor: colors.onAccent,
                  disabledBackgroundColor: colors.line,
                  disabledForegroundColor: colors.ink3,
                  textStyle: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                  ),
                  padding: const EdgeInsets.symmetric(
                    horizontal: LoomSpace.s16,
                    vertical: LoomSpace.s8,
                  ),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(LoomRadius.control),
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
            ),

            // Overflow menu (delete — only for existing skills)
            if (!_isNew)
              PopupMenuButton<_EditorAction>(
                key: const ValueKey('editor-overflow-menu'),
                icon: const Icon(Icons.more_vert),
                onSelected: (action) {
                  if (action == _EditorAction.delete) _delete();
                },
                itemBuilder: (_) => [
                  PopupMenuItem(
                    key: const ValueKey('editor-delete-item'),
                    value: _EditorAction.delete,
                    child: Row(
                      children: [
                        Icon(Icons.delete_outline,
                            size: 16, color: colors.danger),
                        const SizedBox(width: LoomSpace.s8),
                        Text(
                          'Delete',
                          style: TextStyle(
                            fontSize: 13,
                            color: colors.danger,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
          ],
        ),
        body: _loading
            ? const Center(child: CircularProgressIndicator())
            : _errorMessage != null
                ? Center(
                    child: Text(
                      _errorMessage!,
                      style: TextStyle(color: colors.danger),
                    ),
                  )
                : _buildForm(colors),
      ),
    );
  }

  Widget _buildForm(LoomColors colors) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(LoomSpace.s24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // ── Name ──────────────────────────────────────────────────────
          _label('Name', colors),
          const SizedBox(height: LoomSpace.s4),
          TextField(
            key: const ValueKey('skill-name-field'),
            controller: _nameCtrl,
            style: TextStyle(fontSize: 14, color: colors.ink),
            decoration: _inputDecoration('Skill name', colors),
          ),
          const SizedBox(height: LoomSpace.s16),

          // ── Description ───────────────────────────────────────────────
          _label('Description', colors),
          const SizedBox(height: LoomSpace.s4),
          TextField(
            key: const ValueKey('skill-desc-field'),
            controller: _descCtrl,
            maxLines: 3,
            style: TextStyle(fontSize: 14, color: colors.ink),
            decoration: _inputDecoration('Short description…', colors),
          ),
          const SizedBox(height: LoomSpace.s16),

          // ── Tags ──────────────────────────────────────────────────────
          _label('Tags', colors),
          const SizedBox(height: LoomSpace.s4),
          TagChipInput(
            initialTags: _tags,
            onChanged: (tags) => setState(() => _tags = tags.toList()),
          ),
          const SizedBox(height: LoomSpace.s16),

          // ── Content ───────────────────────────────────────────────────
          Row(
            children: [
              Expanded(child: _label('Content', colors)),
              TextButton.icon(
                key: const ValueKey('preview-toggle-text-btn'),
                onPressed: () =>
                    setState(() => _showPreview = !_showPreview),
                icon: Icon(
                  _showPreview
                      ? Icons.edit_outlined
                      : Icons.preview_outlined,
                  size: 14,
                  color: colors.accent,
                ),
                label: Text(
                  _showPreview ? 'Edit' : 'Preview',
                  style: TextStyle(fontSize: 12, color: colors.accent),
                ),
              ),
            ],
          ),
          const SizedBox(height: LoomSpace.s4),
          if (_showPreview)
            Container(
              key: const ValueKey('markdown-preview-container'),
              width: double.infinity,
              padding: const EdgeInsets.all(LoomSpace.s16),
              decoration: BoxDecoration(
                color: colors.surface,
                border: Border.all(color: colors.line),
                borderRadius: BorderRadius.circular(LoomRadius.card),
              ),
              child: _contentCtrl.text.isEmpty
                  ? Text(
                      'Nothing to preview.',
                      style: TextStyle(color: colors.ink3, fontSize: 13),
                    )
                  : MarkdownPreview(data: _contentCtrl.text),
            )
          else
            TextField(
              key: const ValueKey('skill-content-field'),
              controller: _contentCtrl,
              maxLines: null,
              minLines: 10,
              style: TextStyle(
                fontSize: 13,
                color: colors.ink,
                fontFamily: 'monospace',
              ),
              decoration: _inputDecoration(
                'Write Markdown content…',
                colors,
              ),
            ),
        ],
      ),
    );
  }

  Widget _label(String text, LoomColors colors) {
    return Text(
      text,
      style: TextStyle(
        fontSize: 12,
        fontWeight: FontWeight.w600,
        color: colors.ink2,
        letterSpacing: 0.3,
      ),
    );
  }

  InputDecoration _inputDecoration(String hint, LoomColors colors) {
    return InputDecoration(
      hintText: hint,
      hintStyle: TextStyle(fontSize: 13, color: colors.ink3),
      contentPadding: const EdgeInsets.symmetric(
        horizontal: LoomSpace.s12,
        vertical: LoomSpace.s12,
      ),
      isDense: true,
      filled: true,
      fillColor: colors.surface,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(LoomRadius.control),
        borderSide: BorderSide(color: colors.line),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(LoomRadius.control),
        borderSide: BorderSide(color: colors.line),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(LoomRadius.control),
        borderSide: BorderSide(color: colors.accent),
      ),
    );
  }
}

enum _EditorAction { delete }
