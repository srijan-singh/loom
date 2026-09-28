import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:loom_ui/models/session.dart';
import 'package:loom_ui/router/router.dart';
import 'package:loom_ui/services/api_client.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/components/loom_button.dart';
import 'package:loom_ui/ui/components/session_row.dart';
import 'package:loom_ui/ui/components/stat_card.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

// ---------------------------------------------------------------------------
// Riverpod providers
// ---------------------------------------------------------------------------

final _skillsCountProvider = FutureProvider.autoDispose<int>((ref) async {
  final list = await ref.watch(apiClientProvider).getSkills();
  return list.length;
});

final _mcpsCountProvider = FutureProvider.autoDispose<int>((ref) async {
  final list = await ref.watch(apiClientProvider).getMcps();
  return list
      .where((m) => (m['status'] as String?)?.toUpperCase() == 'CONNECTED')
      .length;
});

final _agentsCountProvider = FutureProvider.autoDispose<int>((ref) async {
  final list = await ref.watch(apiClientProvider).getAgents();
  return list.length;
});

final _recentSessionsProvider =
    FutureProvider.autoDispose<List<Session>>((ref) async {
  final list = await ref.watch(apiClientProvider).getRecentSessions(limit: 5);
  return list
      .map((j) => Session.fromJson(j as Map<String, dynamic>))
      .toList();
});

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colors = LoomColors.of(context);
    final skillsAsync = ref.watch(_skillsCountProvider);
    final mcpsAsync = ref.watch(_mcpsCountProvider);
    final agentsAsync = ref.watch(_agentsCountProvider);
    final sessionsAsync = ref.watch(_recentSessionsProvider);

    // New-user: show checklist instead of stat cards when everything is zero
    final allLoaded = skillsAsync.hasValue &&
        mcpsAsync.hasValue &&
        agentsAsync.hasValue;
    final isNewUser = allLoaded &&
        skillsAsync.value == 0 &&
        mcpsAsync.value == 0 &&
        agentsAsync.value == 0;

    return Scaffold(
      backgroundColor: colors.bg,
      body: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(28, 24, 28, 40),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ----------------------------------------------------------------
            // Greeting
            // ----------------------------------------------------------------
            Text(_greetingLine(), style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 2),
            Text(
              "Here's what's happening.",
              style: TextStyle(
                  fontSize: 14,
                  color: colors.ink2,
                  height: 22 / 14),
            ),
            const SizedBox(height: 20),

            // ----------------------------------------------------------------
            // Stats row  OR  new-user setup checklist
            // ----------------------------------------------------------------
            if (!isNewUser) ...[
              Row(
                children: [
                  Expanded(
                    child: StatCard(
                      label: 'Skills configured',
                      count: skillsAsync.valueOrNull,
                      loading: skillsAsync.isLoading,
                      hasError: skillsAsync.hasError,
                      onRetry: () => ref.invalidate(_skillsCountProvider),
                      onTap: () => context.go(Routes.library),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: StatCard(
                      label: 'MCPs connected',
                      count: mcpsAsync.valueOrNull,
                      loading: mcpsAsync.isLoading,
                      hasError: mcpsAsync.hasError,
                      onRetry: () => ref.invalidate(_mcpsCountProvider),
                      onTap: () => context.go(Routes.library),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: StatCard(
                      label: 'Agents defined',
                      count: agentsAsync.valueOrNull,
                      loading: agentsAsync.isLoading,
                      hasError: agentsAsync.hasError,
                      onRetry: () => ref.invalidate(_agentsCountProvider),
                      onTap: () => context.go(Routes.library),
                    ),
                  ),
                ],
              ),
            ] else ...[
              _SetupChecklist(colors: colors),
            ],
            const SizedBox(height: 28),

            // ----------------------------------------------------------------
            // Recent Sessions
            // ----------------------------------------------------------------
            Row(
              children: [
                Text('Recent sessions',
                    style: Theme.of(context).textTheme.titleMedium),
                const Spacer(),
                GestureDetector(
                  onTap: () {},
                  child: Text('View all',
                      style: TextStyle(
                          fontSize: 12.5,
                          color: colors.accentInk,
                          decoration: TextDecoration.underline,
                          decorationColor: colors.accentInk)),
                ),
              ],
            ),
            const SizedBox(height: 4),
            sessionsAsync.when(
              loading: () => _SkeletonRows(colors: colors),
              error: (_, __) => EmptyState(
                icon: Icons.error_outline_rounded,
                headline: 'Could not load sessions',
                body: 'Make sure the Loom engine is running.',
                colors: colors,
              ),
              data: (sessions) => sessions.isEmpty
                  ? EmptyState(
                      icon: Icons.history_rounded,
                      headline: 'Run your first workflow',
                      body: 'Sessions appear here once a workflow runs. A template is the fastest way to start.',
                      actionLabel: 'Use a template',
                      onAction: () => context.go(Routes.templates),
                      colors: colors,
                    )
                  : Column(
                      children: sessions
                          .map((s) => SessionRow(
                                session: s,
                                workflowName: s.workflowDefinitionId,
                                onTap: () => context
                                    .go('${Routes.sessions}/${s.id}'),
                              ))
                          .toList(),
                    ),
            ),
            const SizedBox(height: 28),

            // ----------------------------------------------------------------
            // Quick Start
            // ----------------------------------------------------------------
            Row(
              children: [
                LoomButton(
                  label: '+ New workspace',
                  onPressed: () => context.go(Routes.workspacesNew),
                ),
                const SizedBox(width: 8),
                LoomButton(
                  label: 'Use a template',
                  variant: LoomButtonVariant.secondary,
                  onPressed: () => context.go(Routes.templates),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  static String _greetingLine() {
    final h = DateTime.now().hour;
    if (h < 12) return 'Good morning';
    if (h < 17) return 'Good afternoon';
    return 'Good evening';
  }
}

// ---------------------------------------------------------------------------
// New-user setup checklist
// ---------------------------------------------------------------------------

class _SetupChecklist extends StatelessWidget {
  final LoomColors colors;
  const _SetupChecklist({required this.colors});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        border: Border.all(color: colors.line),
        borderRadius: BorderRadius.circular(LoomRadius.card),
      ),
      child: Column(
        children: [
          _CheckItem(
            title: 'Connect your AI provider',
            subtitle: 'Done',
            done: true,
            colors: colors,
          ),
          _CheckItem(
            title: 'Connect a tool',
            subtitle:
                'Give agents access to GitHub, Figma, or your files.',
            done: false,
            colors: colors,
            showChevron: true,
            onTap: () => {},
          ),
          _CheckItem(
            title: 'Add a skill',
            subtitle: 'Write the instructions an agent follows.',
            done: false,
            colors: colors,
            showChevron: true,
            onTap: () => {},
          ),
          _CheckItem(
            title: 'Create an agent',
            subtitle: 'Pair a skill with the tools it may use.',
            done: false,
            colors: colors,
            showChevron: true,
            isLast: true,
            onTap: () => {},
          ),
        ],
      ),
    );
  }
}

class _CheckItem extends StatelessWidget {
  final String title;
  final String subtitle;
  final bool done;
  final LoomColors colors;
  final bool showChevron;
  final bool isLast;
  final VoidCallback? onTap;

  const _CheckItem({
    required this.title,
    required this.subtitle,
    required this.done,
    required this.colors,
    this.showChevron = false,
    this.isLast = false,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          border: isLast
              ? null
              : Border(
                  bottom: BorderSide(color: colors.line)),
        ),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: onTap,
            borderRadius: BorderRadius.vertical(
              top: isLast ? Radius.zero : Radius.zero,
              bottom: isLast
                  ? const Radius.circular(LoomRadius.card)
                  : Radius.zero,
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(
                  horizontal: 16, vertical: 12),
              child: Row(
                children: [
                  // Circle check box
                  Container(
                    width: 20,
                    height: 20,
                    decoration: BoxDecoration(
                      color: done ? colors.success : Colors.transparent,
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: done
                            ? colors.success
                            : colors.lineStrong,
                        width: 1.5,
                      ),
                    ),
                    child: done
                        ? const Icon(Icons.check_rounded,
                            size: 12, color: Colors.white)
                        : null,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(title,
                            style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w500,
                                color: done
                                    ? colors.ink2
                                    : colors.ink)),
                        Text(subtitle,
                            style: TextStyle(
                                fontSize: 12.5, color: colors.ink2)),
                      ],
                    ),
                  ),
                  if (showChevron)
                    Icon(Icons.chevron_right_rounded,
                        size: 18, color: colors.ink3),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Three skeleton session rows — shown during loading
// ---------------------------------------------------------------------------

class _SkeletonRows extends StatelessWidget {
  final LoomColors colors;
  const _SkeletonRows({required this.colors});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: List.generate(
        3,
        (i) => Container(
          padding: const EdgeInsets.symmetric(vertical: 12),
          decoration: BoxDecoration(
            border: Border(
                top: BorderSide(
                    color: i == 0 ? Colors.transparent : colors.line,
                    width: 1)),
          ),
          child: Row(
            children: [
              SkeletonLoader(width: 8, height: 8, colors: colors),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    SkeletonLoader(width: 160, height: 14, colors: colors),
                    const SizedBox(height: 4),
                    SkeletonLoader(width: 80, height: 12, colors: colors),
                  ],
                ),
              ),
              SkeletonLoader(width: 56, height: 12, colors: colors),
            ],
          ),
        ),
      ),
    );
  }
}
