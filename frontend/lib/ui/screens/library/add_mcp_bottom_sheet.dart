import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:loom_ui/providers/library_providers.dart';
import 'package:loom_ui/ui/components/loom_button.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Supported MCP connection types, in dropdown order.
const _mcpTypes = ['GITHUB', 'FIGMA', 'FILESYSTEM', 'CUSTOM'];

/// Config field label for each connection type.
String _configLabel(String type) => switch (type) {
      'GITHUB' => 'Repository URL or org',
      'FIGMA' => 'Team or file URL',
      'FILESYSTEM' => 'Local path',
      _ => 'Config JSON',
    };

/// Config map key used for non-CUSTOM types.
String _configKey(String type) => switch (type) {
      'FILESYSTEM' => 'path',
      _ => 'url',
    };

/// Modal bottom sheet for adding an MCP connection.
///
/// Use [AddMCPBottomSheet.show] to display it. On successful submit it calls
/// `mcpsProvider.notifier.createMcp` (which refreshes the list) and dismisses.
class AddMCPBottomSheet extends ConsumerStatefulWidget {
  const AddMCPBottomSheet({super.key});

  static Future<void> show(BuildContext context) {
    final colors = LoomColors.of(context);
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: colors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(
          top: Radius.circular(LoomRadius.window),
        ),
      ),
      builder: (_) => const AddMCPBottomSheet(),
    );
  }

  @override
  ConsumerState<AddMCPBottomSheet> createState() => _AddMCPBottomSheetState();
}

class _AddMCPBottomSheetState extends ConsumerState<AddMCPBottomSheet> {
  final _nameCtrl = TextEditingController();
  final _configCtrl = TextEditingController();
  String _type = _mcpTypes.first;
  bool _submitting = false;
  String? _nameError;
  String? _configError;
  String? _submitError;

  @override
  void dispose() {
    _nameCtrl.dispose();
    _configCtrl.dispose();
    super.dispose();
  }

  InputDecoration _decoration(LoomColors colors, String label,
      {String? errorText}) {
    OutlineInputBorder border(Color c) => OutlineInputBorder(
          borderRadius: BorderRadius.circular(LoomRadius.control),
          borderSide: BorderSide(color: c),
        );
    return InputDecoration(
      labelText: label,
      errorText: errorText,
      labelStyle: TextStyle(fontSize: 13, color: colors.ink3),
      isDense: true,
      filled: true,
      fillColor: colors.sunken,
      border: border(colors.line),
      enabledBorder: border(colors.line),
      focusedBorder: border(colors.accent),
      errorBorder: border(colors.danger),
      focusedErrorBorder: border(colors.danger),
    );
  }

  Future<void> _submit() async {
    final name = _nameCtrl.text.trim();
    final configText = _configCtrl.text.trim();

    String? nameError;
    String? configError;
    Map<String, dynamic>? config;

    if (name.isEmpty) nameError = 'Name is required';

    if (configText.isNotEmpty) {
      if (_type == 'CUSTOM') {
        try {
          final decoded = jsonDecode(configText);
          if (decoded is Map) {
            config = Map<String, dynamic>.from(decoded);
          } else {
            configError = 'Config must be a JSON object';
          }
        } on FormatException {
          configError = 'Invalid JSON';
        }
      } else {
        config = {_configKey(_type): configText};
      }
    }

    if (nameError != null || configError != null) {
      setState(() {
        _nameError = nameError;
        _configError = configError;
      });
      return;
    }

    setState(() {
      _nameError = null;
      _configError = null;
      _submitError = null;
      _submitting = true;
    });

    try {
      await ref.read(mcpsProvider.notifier).createMcp({
        'name': name,
        'type': _type,
        if (config != null) 'config': config,
      });
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      if (mounted) {
        setState(() {
          _submitting = false;
          _submitError = 'Failed to add connection: $e';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);
    final isCustom = _type == 'CUSTOM';

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(LoomSpace.s24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Add MCP Connection',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
                color: colors.ink,
              ),
            ),
            const SizedBox(height: LoomSpace.s16),
            TextField(
              key: const ValueKey('mcp-name-field'),
              controller: _nameCtrl,
              enabled: !_submitting,
              style: TextStyle(fontSize: 13, color: colors.ink),
              decoration: _decoration(colors, 'Name', errorText: _nameError),
            ),
            const SizedBox(height: LoomSpace.s12),
            DropdownButtonFormField<String>(
              key: const ValueKey('mcp-type-dropdown'),
              initialValue: _type,
              isExpanded: true,
              dropdownColor: colors.surface,
              style: TextStyle(fontSize: 13, color: colors.ink),
              decoration: _decoration(colors, 'Type'),
              items: [
                for (final t in _mcpTypes)
                  DropdownMenuItem(value: t, child: Text(t)),
              ],
              onChanged: _submitting
                  ? null
                  : (v) {
                      if (v != null) {
                        setState(() {
                          _type = v;
                          _configError = null;
                        });
                      }
                    },
            ),
            const SizedBox(height: LoomSpace.s12),
            TextField(
              key: const ValueKey('mcp-config-field'),
              controller: _configCtrl,
              enabled: !_submitting,
              minLines: isCustom ? 4 : 1,
              maxLines: isCustom ? 8 : 1,
              style: TextStyle(
                fontSize: 13,
                color: colors.ink,
                fontFamily: isCustom ? 'monospace' : null,
              ),
              decoration: _decoration(
                colors,
                _configLabel(_type),
                errorText: _configError,
              ),
            ),
            if (_submitError != null) ...[
              const SizedBox(height: LoomSpace.s12),
              Text(
                _submitError!,
                key: const ValueKey('mcp-submit-error'),
                style: TextStyle(fontSize: 12, color: colors.danger),
              ),
            ],
            const SizedBox(height: LoomSpace.s24),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                LoomButton(
                  key: const ValueKey('mcp-cancel-btn'),
                  label: 'Cancel',
                  variant: LoomButtonVariant.secondary,
                  onPressed:
                      _submitting ? null : () => Navigator.of(context).pop(),
                ),
                const SizedBox(width: LoomSpace.s8),
                LoomButton(
                  key: const ValueKey('mcp-submit-btn'),
                  label: 'Add Connection',
                  loading: _submitting,
                  onPressed: _submit,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
