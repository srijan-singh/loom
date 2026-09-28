import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:loom_ui/router/router.dart';
import 'package:loom_ui/services/api_client.dart';
import 'package:loom_ui/ui/components/loom_button.dart';
import 'package:loom_ui/ui/components/loom_text_field.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

// ---------------------------------------------------------------------------
// Test state — discriminated union drives the inline status area.
// ---------------------------------------------------------------------------

enum _TestState { idle, testing, valid, rejected, unreachable }

// ---------------------------------------------------------------------------
// Hardcoded fallback models (used when API fetch returns empty)
// ---------------------------------------------------------------------------

const _claudeFallback = [
  'claude-3-5-sonnet-20241022',
  'claude-3-haiku-20240307',
];
const _gptFallback = [
  'gpt-4o',
  'gpt-4o-mini',
  'gpt-4-turbo',
];

/// Route: /onboarding/api-key  (Step 1 of 2)
class OnboardingApiKeyScreen extends ConsumerStatefulWidget {
  const OnboardingApiKeyScreen({super.key});

  @override
  ConsumerState<OnboardingApiKeyScreen> createState() =>
      _OnboardingApiKeyScreenState();
}

class _OnboardingApiKeyScreenState
    extends ConsumerState<OnboardingApiKeyScreen> {
  String _provider = 'claude';
  final _keyController = TextEditingController();

  List<String> _models = _claudeFallback;
  String? _selectedModel;

  _TestState _testState = _TestState.idle;
  bool _saving = false;
  String? _keyError; // inline field-level error (empty key)

  @override
  void initState() {
    super.initState();
    _selectedModel = _claudeFallback.first;
  }

  @override
  void dispose() {
    _keyController.dispose();
    super.dispose();
  }

  void _switchProvider(String provider) {
    setState(() {
      _provider = provider;
      _models = provider == 'claude' ? _claudeFallback : _gptFallback;
      _selectedModel = _models.first;
      _testState = _TestState.idle;
      _keyError = null;
    });
  }

  Future<void> _test() async {
    final key = _keyController.text.trim();
    if (key.isEmpty) {
      setState(() => _keyError = 'Enter your API key.');
      return;
    }
    setState(() {
      _testState = _TestState.testing;
      _keyError = null;
    });

    final client = ref.read(apiClientProvider);
    try {
      await client.testLlm(_provider, key, _selectedModel ?? '');
      // Key accepted — fetch real model list from provider
      final fetched = await client.getModels(_provider);
      if (!mounted) return;
      setState(() {
        _testState = _TestState.valid;
        if (fetched.isNotEmpty) {
          _models = fetched;
          _selectedModel = fetched.first;
        }
      });
    } on LlmTestException catch (e) {
      if (!mounted) return;
      setState(() => _testState = switch (e.failure) {
            LlmTestFailure.rejected => _TestState.rejected,
            LlmTestFailure.unreachable => _TestState.unreachable,
          });
    } catch (_) {
      if (!mounted) return;
      setState(() => _testState = _TestState.unreachable);
    }
  }

  Future<void> _save() async {
    final key = _keyController.text.trim();
    if (key.isEmpty) {
      setState(() => _keyError = 'Enter your API key.');
      return;
    }
    // Run test first if not already validated
    if (_testState != _TestState.valid) {
      await _test();
      if (_testState != _TestState.valid) return;
    }
    setState(() => _saving = true);
    final storage = ref.read(storageServiceProvider);
    await storage.saveApiKey(_provider, key, _selectedModel ?? '');
    await storage.completeOnboarding();
    if (!mounted) return;
    setState(() => _saving = false);
    context.go(Routes.dashboard);
  }

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);

    return Scaffold(
      backgroundColor: colors.bg,
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(48),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Back + step indicator
                Row(
                  children: [
                    GestureDetector(
                      onTap: () => context.go(Routes.onboardingWelcome),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.arrow_back_ios_new_rounded,
                              size: 14, color: colors.ink2),
                          const SizedBox(width: 4),
                          Text('Back',
                              style: TextStyle(
                                  fontSize: 13, color: colors.ink2)),
                        ],
                      ),
                    ),
                    const Spacer(),
                    Text('Step 1 of 2',
                        style: TextStyle(fontSize: 12, color: colors.ink3)),
                  ],
                ),
                const SizedBox(height: 24),

                Text('Connect your AI provider',
                    style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 6),
                Text(
                  'Your key stays in your OS keychain. Loom never sees it.',
                  style: TextStyle(fontSize: 13.5, color: colors.ink2),
                ),
                const SizedBox(height: 24),

                // ── Provider segment ──────────────────────────────────────
                Text('Provider',
                    style: TextStyle(
                        fontSize: 12.5,
                        fontWeight: FontWeight.w500,
                        color: colors.ink2)),
                const SizedBox(height: 8),
                _ProviderSegment(
                  value: _provider,
                  colors: colors,
                  onChanged: _switchProvider,
                ),
                const SizedBox(height: 16),

                // ── API key ───────────────────────────────────────────────
                LoomTextField(
                  label: 'API key',
                  hint: _provider == 'claude' ? 'sk-ant-…' : 'sk-…',
                  obscure: true,
                  controller: _keyController,
                  errorText: _keyError,
                  onChanged: (_) => setState(() {
                    _keyError = null;
                    if (_testState != _TestState.idle) {
                      _testState = _TestState.idle;
                    }
                  }),
                ),
                const SizedBox(height: 16),

                // ── Model dropdown ────────────────────────────────────────
                Text('Model',
                    style: TextStyle(
                        fontSize: 12.5,
                        fontWeight: FontWeight.w500,
                        color: colors.ink2)),
                const SizedBox(height: 8),
                _ModelDropdown(
                  models: _models,
                  selected: _selectedModel ?? _models.first,
                  colors: colors,
                  onChanged: (m) => setState(() => _selectedModel = m),
                ),
                const SizedBox(height: 6),
                GestureDetector(
                  onTap: () {
                    // TODO: show custom model ID dialog
                  },
                  child: Text('Use a custom model ID',
                      style: TextStyle(
                          fontSize: 12.5,
                          color: colors.accentInk,
                          decoration: TextDecoration.underline,
                          decorationColor: colors.accentInk)),
                ),
                const SizedBox(height: 20),

                // ── Inline test status ────────────────────────────────────
                _TestStatusRow(state: _testState, colors: colors),
                const SizedBox(height: 12),

                // ── Buttons ───────────────────────────────────────────────
                Row(
                  children: [
                    LoomButton(
                      label: _testState == _TestState.testing
                          ? 'Testing…'
                          : 'Test key',
                      variant: LoomButtonVariant.secondary,
                      loading: _testState == _TestState.testing,
                      onPressed:
                          _testState == _TestState.testing ? null : _test,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: LoomButton(
                        label: 'Save and continue',
                        loading: _saving,
                        onPressed: _saving ? null : _save,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Provider segment control (replaces Radio widgets)
// ---------------------------------------------------------------------------

class _ProviderSegment extends StatelessWidget {
  final String value;
  final LoomColors colors;
  final void Function(String) onChanged;

  const _ProviderSegment({
    required this.value,
    required this.colors,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        _SegButton(
          label: 'Claude (Anthropic)',
          selected: value == 'claude',
          colors: colors,
          onTap: () => onChanged('claude'),
        ),
        const SizedBox(width: 8),
        _SegButton(
          label: 'GPT (OpenAI)',
          selected: value == 'gpt',
          colors: colors,
          onTap: () => onChanged('gpt'),
        ),
      ],
    );
  }
}

class _SegButton extends StatelessWidget {
  final String label;
  final bool selected;
  final LoomColors colors;
  final VoidCallback onTap;

  const _SegButton({
    required this.label,
    required this.selected,
    required this.colors,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          height: LoomSize.control,
          decoration: BoxDecoration(
            color: selected ? colors.accentTint : colors.surface,
            borderRadius: BorderRadius.circular(LoomRadius.control),
            border: Border.all(
              color: selected ? colors.accent : colors.lineStrong,
              width: selected ? 1.5 : 1,
            ),
          ),
          child: Center(
            child: Text(
              label,
              style: TextStyle(
                fontSize: 13.5,
                fontWeight:
                    selected ? FontWeight.w500 : FontWeight.w400,
                color: selected ? colors.accentInk : colors.ink2,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Inline test status row
// ---------------------------------------------------------------------------

class _TestStatusRow extends StatelessWidget {
  final _TestState state;
  final LoomColors colors;

  const _TestStatusRow({required this.state, required this.colors});

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
      _TestState.valid => (
          const Icon(Icons.check_circle_rounded, size: 16),
          'Key accepted.',
        ),
      _TestState.rejected => (
          const Icon(Icons.cancel_rounded, size: 16),
          'That key was rejected. Check it and try again.',
        ),
      _TestState.unreachable => (
          const Icon(Icons.wifi_off_rounded, size: 16),
          "Can't reach the provider. Check your connection.",
        ),
      _TestState.idle => (const SizedBox.shrink(), ''),
    };

    final iconColor = switch (state) {
      _TestState.valid => colors.success,
      _TestState.rejected => colors.danger,
      _TestState.unreachable => colors.warning,
      _ => colors.ink2,
    };

    final textColor = switch (state) {
      _TestState.valid => colors.success,
      _TestState.rejected => colors.danger,
      _TestState.unreachable => colors.warning,
      _ => colors.ink2,
    };

    return Row(
      children: [
        IconTheme(
          data: IconThemeData(color: iconColor, size: 16),
          child: icon,
        ),
        const SizedBox(width: 8),
        Text(message,
            style: TextStyle(fontSize: 13, color: textColor)),
      ],
    );
  }
}

// ---------------------------------------------------------------------------
// Model dropdown
// ---------------------------------------------------------------------------

class _ModelDropdown extends StatelessWidget {
  final List<String> models;
  final String selected;
  final LoomColors colors;
  final void Function(String) onChanged;

  const _ModelDropdown({
    required this.models,
    required this.selected,
    required this.colors,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: LoomSize.control,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(LoomRadius.control),
        border: Border.all(color: colors.lineStrong),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<String>(
          value: models.contains(selected) ? selected : models.first,
          dropdownColor: colors.surface,
          iconEnabledColor: colors.ink2,
          style: TextStyle(color: colors.ink, fontSize: 14),
          isExpanded: true,
          items: models
              .map((m) => DropdownMenuItem(value: m, child: Text(m)))
              .toList(),
          onChanged: (v) => onChanged(v!),
        ),
      ),
    );
  }
}
