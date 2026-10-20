import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:loom_ui/services/api_client.dart';
import 'package:loom_ui/ui/components/loom_button.dart';
import 'package:loom_ui/ui/components/loom_text_field.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

// ---------------------------------------------------------------------------
// Data transfer object — what the form yields on submit.
// ---------------------------------------------------------------------------

class LlmConnectionDraft {
  final String name;
  final String baseUrl;
  final String model;
  final String apiKey;
  final bool isDefault;

  const LlmConnectionDraft({
    required this.name,
    required this.baseUrl,
    required this.model,
    required this.apiKey,
    required this.isDefault,
  });
}

// ---------------------------------------------------------------------------
// Preset definition — one entry in the fixed preset list.
// ---------------------------------------------------------------------------

class _Preset {
  final String label;
  final String baseUrl;
  final List<String> suggestedModels;
  /// Whether this preset requires a real API key (remote services).
  /// Local presets (Ollama, LM Studio) allow an empty key in the UI;
  /// the submitted key is replaced with a non-blank placeholder so the
  /// backend constraint (non-blank apiKey) is satisfied.
  final bool requiresKey;

  const _Preset({
    required this.label,
    required this.baseUrl,
    this.suggestedModels = const [],
    this.requiresKey = true,
  });
}

const _presets = [
  _Preset(
    label: 'OpenAI',
    baseUrl: 'https://api.openai.com/v1',
    suggestedModels: ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo'],
  ),
  _Preset(
    label: 'OpenRouter',
    baseUrl: 'https://openrouter.ai/api/v1',
    suggestedModels: ['openai/gpt-4o', 'anthropic/claude-3-5-sonnet'],
  ),
  _Preset(
    label: 'Groq',
    baseUrl: 'https://api.groq.com/openai/v1',
    suggestedModels: ['llama3-8b-8192', 'mixtral-8x7b-32768'],
  ),
  _Preset(
    label: 'Ollama (local)',
    baseUrl: 'http://localhost:11434/v1',
    suggestedModels: ['llama3', 'mistral', 'phi3'],
    requiresKey: false,
  ),
  _Preset(
    label: 'LM Studio (local)',
    baseUrl: 'http://localhost:1234/v1',
    suggestedModels: [],
    requiresKey: false,
  ),
  _Preset(
    label: 'Custom',
    baseUrl: '',
    suggestedModels: [],
  ),
];

// ---------------------------------------------------------------------------
// Test state for the inline test button.
// ---------------------------------------------------------------------------

enum _TestState { idle, testing, ok, failed, unreachable }

// ---------------------------------------------------------------------------
// LlmConnectionForm
// ---------------------------------------------------------------------------

/// Reusable form for creating an LLM connection (onboarding + settings).
///
/// The host controls the submit button; this widget exposes:
/// - [onSubmit] — called with a [LlmConnectionDraft] when the form is valid
///   and a successful test has been run.
/// - [submitLabel] — label for the submit button (defaults to 'Save').
/// - [isFirst] / [forceDefault] — when true the "Set as default" switch is
///   locked on and the user cannot toggle it.
class LlmConnectionForm extends ConsumerStatefulWidget {
  final void Function(LlmConnectionDraft draft) onSubmit;
  final String submitLabel;
  /// When true the connection must be set as default (locked switch).
  final bool forceDefault;
  final bool submitting;

  const LlmConnectionForm({
    super.key,
    required this.onSubmit,
    this.submitLabel = 'Save',
    this.forceDefault = false,
    this.submitting = false,
  });

  @override
  ConsumerState<LlmConnectionForm> createState() => LlmConnectionFormState();
}

class LlmConnectionFormState extends ConsumerState<LlmConnectionForm> {
  _Preset _selectedPreset = _presets.first;

  final _nameCtrl = TextEditingController();
  final _baseUrlCtrl = TextEditingController();
  final _apiKeyCtrl = TextEditingController();
  final _modelCtrl = TextEditingController();

  bool _isDefault = true;

  _TestState _testState = _TestState.idle;
  String? _testError;
  int _testGeneration = 0;

  // Inline validation errors
  String? _nameError;
  String? _baseUrlError;
  String? _modelError;
  String? _apiKeyError;

  @override
  void initState() {
    super.initState();
    _applyPreset(_presets.first);
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _baseUrlCtrl.dispose();
    _apiKeyCtrl.dispose();
    _modelCtrl.dispose();
    super.dispose();
  }

  void _applyPreset(_Preset preset) {
    setState(() {
      _selectedPreset = preset;
      if (preset.label == 'Custom') {
        // Clear fields so the user can fill them in fresh.
        _nameCtrl.clear();
        _baseUrlCtrl.clear();
      } else {
        _nameCtrl.text = preset.label;
        _baseUrlCtrl.text = preset.baseUrl;
      }
      // Always reset test state when changing preset
      _resetTestState();
    });
  }

  void _resetTestState() {
    _testGeneration++;
    _testState = _TestState.idle;
    _testError = null;
  }

  void _onAnyFieldChange() {
    setState(() {
      _resetTestState();
    });
  }

  bool get _isLocalPreset => !_selectedPreset.requiresKey;

  bool _validate() {
    bool valid = true;

    final name = _nameCtrl.text.trim();
    if (name.isEmpty) {
      _nameError = 'Name is required.';
      valid = false;
    } else {
      _nameError = null;
    }

    final baseUrl = _baseUrlCtrl.text.trim();
    if (baseUrl.isEmpty ||
        (!baseUrl.startsWith('http://') && !baseUrl.startsWith('https://'))) {
      _baseUrlError = 'Base URL must start with http:// or https://.';
      valid = false;
    } else {
      _baseUrlError = null;
    }

    final model = _modelCtrl.text.trim();
    if (model.isEmpty) {
      _modelError = 'Model ID is required.';
      valid = false;
    } else {
      _modelError = null;
    }

    final apiKey = _apiKeyCtrl.text.trim();
    if (_selectedPreset.requiresKey && apiKey.isEmpty) {
      _apiKeyError = 'API key is required for this provider.';
      valid = false;
    } else {
      _apiKeyError = null;
    }

    return valid;
  }

  /// The effective API key sent to the backend.
  /// Local presets allow empty UI key; we send a placeholder so the backend
  /// non-blank constraint is satisfied.
  String get _effectiveApiKey {
    final key = _apiKeyCtrl.text.trim();
    if (key.isNotEmpty) return key;
    // Local presets (Ollama, LM Studio) don't need a real key.
    // Backend requires non-blank; send a sentinel value.
    return 'not-needed';
  }

  String get _trimmedBaseUrl {
    var url = _baseUrlCtrl.text.trim();
    // Remove trailing slash for consistent storage.
    if (url.endsWith('/')) url = url.substring(0, url.length - 1);
    return url;
  }

  Future<void> _runTest() async {
    if (!_validate()) {
      setState(() {});
      return;
    }

    final generation = ++_testGeneration;
    setState(() {
      _testState = _TestState.testing;
      _testError = null;
    });

    final client = ref.read(apiClientProvider);
    try {
      await client.testLlmConnection(
        baseUrl: _trimmedBaseUrl,
        apiKey: _effectiveApiKey,
        model: _modelCtrl.text.trim(),
      );
      if (!mounted || generation != _testGeneration) return;
      setState(() => _testState = _TestState.ok);
    } on LlmTestException catch (e) {
      if (!mounted || generation != _testGeneration) return;
      setState(() {
        _testState = e.failure == LlmTestFailure.unreachable
            ? _TestState.unreachable
            : _TestState.failed;
        _testError = e.message;
      });
    } catch (_) {
      if (!mounted || generation != _testGeneration) return;
      setState(() => _testState = _TestState.unreachable);
    }
  }

  void _submit() {
    if (!_validate()) {
      setState(() {});
      return;
    }
    if (_testState != _TestState.ok) return;

    widget.onSubmit(LlmConnectionDraft(
      name: _nameCtrl.text.trim(),
      baseUrl: _trimmedBaseUrl,
      model: _modelCtrl.text.trim(),
      apiKey: _effectiveApiKey,
      isDefault: widget.forceDefault || _isDefault,
    ));
  }

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        // ── Preset chips ────────────────────────────────────────────────────
        _sectionLabel('Preset', colors),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 6,
          children: _presets.map((preset) {
            final selected = _selectedPreset.label == preset.label;
            return GestureDetector(
              key: ValueKey('preset-${preset.label}'),
              onTap: () => _applyPreset(preset),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 120),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: selected ? colors.accentTint : colors.surface,
                  borderRadius: BorderRadius.circular(LoomRadius.control),
                  border: Border.all(
                    color: selected ? colors.accent : colors.lineStrong,
                    width: selected ? 1.5 : 1,
                  ),
                ),
                child: Text(
                  preset.label,
                  style: TextStyle(
                    fontSize: 13,
                    color: selected ? colors.accentInk : colors.ink2,
                    fontWeight: selected ? FontWeight.w500 : FontWeight.w400,
                  ),
                ),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 16),

        // ── Name ────────────────────────────────────────────────────────────
        LoomTextField(
          key: const ValueKey('llm-name-field'),
          label: 'Name',
          hint: 'My connection',
          controller: _nameCtrl,
          errorText: _nameError,
          onChanged: (_) {
            if (_nameError != null) setState(() => _nameError = null);
            _onAnyFieldChange();
          },
        ),
        const SizedBox(height: 12),

        // ── Base URL ─────────────────────────────────────────────────────────
        LoomTextField(
          key: const ValueKey('llm-base-url-field'),
          label: 'Base URL',
          hint: 'https://api.openai.com/v1',
          controller: _baseUrlCtrl,
          errorText: _baseUrlError,
          onChanged: (_) {
            if (_baseUrlError != null) setState(() => _baseUrlError = null);
            _onAnyFieldChange();
          },
        ),
        const SizedBox(height: 12),

        // ── API Key ──────────────────────────────────────────────────────────
        LoomTextField(
          key: const ValueKey('llm-api-key-field'),
          label: 'API key',
          hint: _isLocalPreset ? 'Optional for local servers' : 'sk-…',
          obscure: true,
          controller: _apiKeyCtrl,
          errorText: _apiKeyError,
          onChanged: (_) {
            if (_apiKeyError != null) setState(() => _apiKeyError = null);
            _onAnyFieldChange();
          },
        ),
        const SizedBox(height: 12),

        // ── Model ID ────────────────────────────────────────────────────────
        LoomTextField(
          key: const ValueKey('llm-model-field'),
          label: 'Model ID',
          hint: 'e.g. gpt-4o',
          controller: _modelCtrl,
          errorText: _modelError,
          onChanged: (_) {
            if (_modelError != null) setState(() => _modelError = null);
            _onAnyFieldChange();
          },
        ),
        // Suggestion chips
        if (_selectedPreset.suggestedModels.isNotEmpty) ...[
          const SizedBox(height: 6),
          Wrap(
            spacing: 6,
            children: _selectedPreset.suggestedModels.map((m) {
              return GestureDetector(
                key: ValueKey('model-suggestion-$m'),
                onTap: () {
                  _modelCtrl.text = m;
                  if (_modelError != null) setState(() => _modelError = null);
                  _onAnyFieldChange();
                },
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: colors.sunken,
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: colors.line),
                  ),
                  child: Text(
                    m,
                    style: TextStyle(fontSize: 12, color: colors.ink2),
                  ),
                ),
              );
            }).toList(),
          ),
        ],
        const SizedBox(height: 12),

        // ── Set as default ───────────────────────────────────────────────────
        Row(
          children: [
            Expanded(
              child: Text(
                'Set as default',
                style: TextStyle(fontSize: 13.5, color: colors.ink),
              ),
            ),
            Switch(
              key: const ValueKey('llm-default-switch'),
              value: widget.forceDefault || _isDefault,
              onChanged: widget.forceDefault
                  ? null
                  : (v) => setState(() => _isDefault = v),
              activeTrackColor: colors.accent,
            ),
          ],
        ),
        const SizedBox(height: 16),

        // ── Inline test status ───────────────────────────────────────────────
        _TestStatusArea(
          state: _testState,
          errorMessage: _testError,
          colors: colors,
        ),
        if (_testState != _TestState.idle) const SizedBox(height: 12),

        // ── Action buttons ───────────────────────────────────────────────────
        // SizedBox.expand makes the Row fill full available width so that
        // the non-flex first button (LoomButton) doesn't inherit a collapsed
        // column width in test / scroll contexts.
        Row(
          children: [
            LoomButton(
              key: const ValueKey('llm-test-btn'),
              label: _testState == _TestState.testing ? 'Testing…' : 'Test connection',
              variant: LoomButtonVariant.secondary,
              loading: _testState == _TestState.testing,
              onPressed: _testState == _TestState.testing ? null : _runTest,
            ),
            const SizedBox(width: 8),
            Expanded(
              child: LoomButton(
                key: const ValueKey('llm-submit-btn'),
                label: widget.submitting ? 'Saving…' : widget.submitLabel,
                loading: widget.submitting,
                onPressed: (widget.submitting || _testState != _TestState.ok)
                    ? null
                    : _submit,
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _sectionLabel(String text, LoomColors colors) => Text(
        text,
        style: TextStyle(
          fontSize: 12.5,
          fontWeight: FontWeight.w500,
          color: colors.ink2,
        ),
      );
}

// ---------------------------------------------------------------------------
// Inline test status row
// ---------------------------------------------------------------------------

class _TestStatusArea extends StatelessWidget {
  final _TestState state;
  final String? errorMessage;
  final LoomColors colors;

  const _TestStatusArea({
    required this.state,
    required this.errorMessage,
    required this.colors,
  });

  @override
  Widget build(BuildContext context) {
    if (state == _TestState.idle) return const SizedBox.shrink();

    final (Widget icon, String message) = switch (state) {
      _TestState.testing => (
          const SizedBox(
            width: 14,
            height: 14,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
          'Testing…',
        ),
      _TestState.ok => (
          const Icon(Icons.check_circle_rounded, size: 16),
          'Connection verified.',
        ),
      _TestState.failed => (
          const Icon(Icons.cancel_rounded, size: 16),
          errorMessage ?? 'Connection rejected. Check your API key and model.',
        ),
      _TestState.unreachable => (
          const Icon(Icons.wifi_off_rounded, size: 16),
          errorMessage ?? "Can't reach the endpoint. Check your base URL and connection.",
        ),
      _TestState.idle => (const SizedBox.shrink(), ''),
    };

    final iconColor = switch (state) {
      _TestState.ok => colors.success,
      _TestState.failed => colors.danger,
      _TestState.unreachable => colors.warning,
      _ => colors.ink2,
    };

    final textColor = switch (state) {
      _TestState.ok => colors.success,
      _TestState.failed => colors.danger,
      _TestState.unreachable => colors.warning,
      _ => colors.ink2,
    };

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(top: 1),
          child: IconTheme(
            data: IconThemeData(color: iconColor, size: 16),
            child: icon,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            message,
            style: TextStyle(fontSize: 13, color: textColor),
          ),
        ),
      ],
    );
  }
}
