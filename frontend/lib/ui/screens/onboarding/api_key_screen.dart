import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:loom_ui/providers/llm_providers.dart';
import 'package:loom_ui/router/router.dart';
import 'package:loom_ui/ui/components/llm_connection_form.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

/// Route: /onboarding/api-key  (Step 1 of 2)
class OnboardingApiKeyScreen extends ConsumerStatefulWidget {
  const OnboardingApiKeyScreen({super.key});

  @override
  ConsumerState<OnboardingApiKeyScreen> createState() =>
      _OnboardingApiKeyScreenState();
}

class _OnboardingApiKeyScreenState
    extends ConsumerState<OnboardingApiKeyScreen> {
  bool _saving = false;

  Future<void> _handleSubmit(LlmConnectionDraft draft) async {
    setState(() => _saving = true);
    try {
      await ref.read(llmConnectionsProvider.notifier).create(
            name: draft.name,
            baseUrl: draft.baseUrl,
            model: draft.model,
            apiKey: draft.apiKey,
            isDefault: true,
          );
      final storage = ref.read(storageServiceProvider);
      await storage.completeOnboarding();
      if (!mounted) return;
      context.go(Routes.dashboard);
    } catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to save connection: $e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = LoomColors.of(context);

    return Scaffold(
      backgroundColor: colors.bg,
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480),
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

                Text('Connect an AI model',
                    style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 6),
                Text(
                  'Configure any OpenAI-compatible endpoint to power your agents.',
                  style: TextStyle(fontSize: 13.5, color: colors.ink2),
                ),
                const SizedBox(height: 24),

                LlmConnectionForm(
                  key: const ValueKey('onboarding-llm-form'),
                  onSubmit: _handleSubmit,
                  submitLabel: 'Save and continue',
                  forceDefault: true,
                  submitting: _saving,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
