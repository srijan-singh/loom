import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:loom_ui/models/agent_definition.dart';
import 'package:loom_ui/models/mcp_connection.dart';
import 'package:loom_ui/models/skill.dart';
import 'package:loom_ui/providers/library_providers.dart';
import 'package:loom_ui/ui/components/agent_card.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/components/mcp_card.dart';
import 'package:loom_ui/ui/components/skill_card.dart';
import 'package:loom_ui/ui/screens/library/agents_tab_view.dart';
import 'package:loom_ui/ui/screens/library/mcps_tab_view.dart';
import 'package:loom_ui/ui/screens/library/skill_editor_screen.dart';
import 'package:loom_ui/ui/screens/library/skills_tab_view.dart';
import 'package:loom_ui/ui/screens/library_screen.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

// ---------------------------------------------------------------------------
// Test Data & Fakes
// ---------------------------------------------------------------------------

const _sampleSkill1 = Skill(
  id: 'sk-1',
  name: 'Code Reviewer',
  description: 'Reviews pull requests.',
  content: '## Review guidelines',
  tags: ['git', 'review'],
  createdAt: 100,
  updatedAt: 100,
);

const _sampleSkill2 = Skill(
  id: 'sk-2',
  name: 'Triage Bot',
  description: 'Triages issues.',
  content: '',
  tags: ['bugs'],
  createdAt: 200,
  updatedAt: 200,
);

const _sampleMcp1 = MCPConnection(
  id: 'm-1',
  name: 'Repo Server',
  type: 'GITHUB',
  status: MCPStatus.CONNECTED,
  tools: ['search', 'read'],
  detail: 'github.com/acme',
  createdAt: 100,
);

const _sampleMcp2 = MCPConnection(
  id: 'm-2',
  name: 'Local Files',
  type: 'FILESYSTEM',
  status: MCPStatus.DISCONNECTED,
  createdAt: 200,
);

const _sampleAgent1 = AgentDefinition(
  id: 'ag-1',
  name: 'PR Review Bot',
  roleDescription: 'Automates PR reviews.',
  skillId: 'sk-1',
  allowedMcpIds: ['m-1'],
  createdAt: 100,
  updatedAt: 100,
);

const _sampleAgent2 = AgentDefinition(
  id: 'ag-2',
  name: 'Support Agent',
  roleDescription: 'Answers user questions.',
  skillId: null,
  allowedMcpIds: [],
  createdAt: 200,
  updatedAt: 200,
);

class _FakeSkillsNotifier extends SkillsNotifier {
  _FakeSkillsNotifier(List<Skill> skills) : _skills = List.from(skills);
  final List<Skill> _skills;

  @override
  Future<List<Skill>> build() async => List.from(_skills);

  @override
  Future<void> deleteSkill(String id) async {
    _skills.removeWhere((s) => s.id == id);
    ref.invalidateSelf();
  }

  @override
  Future<Skill> createSkill(Skill skill) async {
    final created = skill.copyWith(id: 'sk-new');
    _skills.add(created);
    ref.invalidateSelf();
    return created;
  }

  @override
  Future<Skill> updateSkill(String id, Skill skill) async {
    final idx = _skills.indexWhere((s) => s.id == id);
    if (idx != -1) _skills[idx] = skill;
    ref.invalidateSelf();
    return skill;
  }
}

class _FakeMcpsNotifier extends McpsNotifier {
  _FakeMcpsNotifier(List<MCPConnection> mcps) : _mcps = List.from(mcps);
  final List<MCPConnection> _mcps;

  @override
  Future<List<MCPConnection>> build() async => List.from(_mcps);

  @override
  Future<MCPConnection> createMcp(Map<String, dynamic> payload) async {
    final mcp = MCPConnection(
      id: 'm-new',
      name: payload['name'] as String,
      type: payload['type'] as String,
      createdAt: 0,
    );
    _mcps.add(mcp);
    ref.invalidateSelf();
    return mcp;
  }

  @override
  Future<void> updateMcpStatus(String id, String status) async {
    final idx = _mcps.indexWhere((m) => m.id == id);
    if (idx != -1) {
      _mcps[idx] = _mcps[idx].copyWith(
        status: status == 'CONNECTED'
            ? MCPStatus.CONNECTED
            : MCPStatus.DISCONNECTED,
      );
    }
    ref.invalidateSelf();
  }

  @override
  Future<void> deleteMcp(String id) async {
    _mcps.removeWhere((m) => m.id == id);
    ref.invalidateSelf();
  }
}

class _FakeAgentsNotifier extends AgentsNotifier {
  _FakeAgentsNotifier(List<AgentDefinition> agents)
      : _agents = List.from(agents);
  final List<AgentDefinition> _agents;

  @override
  Future<List<AgentDefinition>> build() async => List.from(_agents);

  @override
  Future<AgentDefinition> createAgent(AgentDefinition agent) async {
    final created = agent.copyWith(id: 'ag-new');
    _agents.add(created);
    ref.invalidateSelf();
    return created;
  }

  @override
  Future<AgentDefinition> updateAgent(
      String id, AgentDefinition agent) async {
    final idx = _agents.indexWhere((a) => a.id == id);
    if (idx != -1) _agents[idx] = agent;
    ref.invalidateSelf();
    return agent;
  }

  @override
  Future<void> deleteAgent(String id) async {
    _agents.removeWhere((a) => a.id == id);
    ref.invalidateSelf();
  }
}

class _ErrorSkillsNotifier extends SkillsNotifier {
  final String message;
  _ErrorSkillsNotifier(this.message);

  @override
  Future<List<Skill>> build() async => throw Exception(message);
}

class _ErrorMcpsNotifier extends McpsNotifier {
  final String message;
  _ErrorMcpsNotifier(this.message);

  @override
  Future<List<MCPConnection>> build() async => throw Exception(message);
}

class _ErrorAgentsNotifier extends AgentsNotifier {
  final String message;
  _ErrorAgentsNotifier(this.message);

  @override
  Future<List<AgentDefinition>> build() async => throw Exception(message);
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

Widget _buildLibraryScreen({
  List<Skill> skills = const [_sampleSkill1, _sampleSkill2],
  List<MCPConnection> mcps = const [_sampleMcp1, _sampleMcp2],
  List<AgentDefinition> agents = const [_sampleAgent1, _sampleAgent2],
}) {
  return ProviderScope(
    overrides: [
      skillsProvider.overrideWith(() => _FakeSkillsNotifier(skills)),
      mcpsProvider.overrideWith(() => _FakeMcpsNotifier(mcps)),
      agentsProvider.overrideWith(() => _FakeAgentsNotifier(agents)),
    ],
    child: MaterialApp(
      theme: LoomTheme.light,
      home: const LibraryScreen(),
    ),
  );
}

Widget _buildRouterApp({
  required String initialLocation,
  List<Skill> skills = const [_sampleSkill1, _sampleSkill2],
  List<MCPConnection> mcps = const [_sampleMcp1, _sampleMcp2],
  List<AgentDefinition> agents = const [_sampleAgent1, _sampleAgent2],
}) {
  final router = GoRouter(
    initialLocation: initialLocation,
    routes: [
      GoRoute(
        path: '/library',
        builder: (_, __) => const LibraryScreen(),
        routes: [
          GoRoute(
            path: 'skills/new',
            name: 'skill-new',
            builder: (context, _) => SkillEditorScreen(
              skillId: null,
              onBack: () => context.canPop()
                  ? context.pop()
                  : context.go('/library'),
            ),
          ),
          GoRoute(
            path: 'skills/:id',
            name: 'skill-detail',
            builder: (context, state) => SkillEditorScreen(
              skillId: state.pathParameters['id'],
              onBack: () => context.canPop()
                  ? context.pop()
                  : context.go('/library'),
            ),
          ),
        ],
      ),
    ],
  );

  return ProviderScope(
    overrides: [
      skillsProvider.overrideWith(() => _FakeSkillsNotifier(skills)),
      mcpsProvider.overrideWith(() => _FakeMcpsNotifier(mcps)),
      agentsProvider.overrideWith(() => _FakeAgentsNotifier(agents)),
    ],
    child: MaterialApp.router(
      theme: LoomTheme.light,
      routerConfig: router,
    ),
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('LibraryScreen', () {
    // ── Requirement 1 ───────────────────────────────────────────────────────
    testWidgets('renders top TabBar with "Skills", "MCPs", and "Agents" tabs',
        (tester) async {
      await tester.pumpWidget(_buildLibraryScreen());
      await tester.pumpAndSettle();

      // TabBar exists
      expect(find.byType(TabBar), findsOneWidget);

      // 3 tabs with expected text labels exist in the tab bar
      expect(find.widgetWithText(Tab, 'Skills'), findsOneWidget);
      expect(find.widgetWithText(Tab, 'MCPs'), findsOneWidget);
      expect(find.widgetWithText(Tab, 'Agents'), findsOneWidget);

      // Initial tab is Skills tab
      expect(find.byType(SkillsTabView), findsOneWidget);
      expect(find.text('Code Reviewer'), findsOneWidget);
      expect(find.text('Triage Bot'), findsOneWidget);
    });

    // ── Requirement 2 ───────────────────────────────────────────────────────
    testWidgets(
        'switching between tabs displays the corresponding view and preserves state (KeepAlive)',
        (tester) async {
      await tester.pumpWidget(_buildLibraryScreen());
      await tester.pumpAndSettle();

      // Verify we are on Skills tab initially
      expect(find.byType(SkillsTabView), findsOneWidget);
      expect(find.byType(SkillCard), findsNWidgets(2));

      // Type a search query in Skills search field that filters out Triage Bot
      await tester.enterText(
        find.byKey(const ValueKey('skills-search')),
        'Reviewer',
      );
      await tester.pumpAndSettle();

      // Only 'Code Reviewer' should be visible in the list
      expect(find.byType(SkillCard), findsOneWidget);
      expect(find.text('Code Reviewer'), findsOneWidget);
      expect(find.text('Triage Bot'), findsNothing);

      // Switch to MCPs tab
      await tester.tap(find.widgetWithText(Tab, 'MCPs'));
      await tester.pumpAndSettle();

      // McpsTabView is now active
      expect(find.byType(McpsTabView), findsOneWidget);
      expect(find.text('Repo Server'), findsOneWidget);
      expect(find.text('Local Files'), findsOneWidget);

      // Switch to Agents tab
      await tester.tap(find.widgetWithText(Tab, 'Agents'));
      await tester.pumpAndSettle();

      // AgentsTabView is now active
      expect(find.byType(AgentsTabView), findsOneWidget);
      expect(find.text('PR Review Bot'), findsOneWidget);

      // Switch back to Skills tab
      await tester.tap(find.widgetWithText(Tab, 'Skills'));
      await tester.pumpAndSettle();

      // Verify search input was preserved across tab switches
      final searchField = tester.widget<TextField>(
        find.byKey(const ValueKey('skills-search')),
      );
      expect(searchField.controller?.text, 'Reviewer');
      expect(find.byType(SkillCard), findsOneWidget);
      expect(find.text('Code Reviewer'), findsOneWidget);
      expect(find.text('Triage Bot'), findsNothing);
    });

    // ── Requirement 3 ───────────────────────────────────────────────────────
    testWidgets(
        'navigating to /library/skills/new displays skill editor in create mode',
        (tester) async {
      await tester.pumpWidget(
        _buildRouterApp(initialLocation: '/library/skills/new'),
      );
      await tester.pumpAndSettle();

      // SkillEditorScreen in create mode
      expect(find.byType(SkillEditorScreen), findsOneWidget);
      expect(find.text('New Skill'), findsOneWidget);

      final nameField = tester.widget<TextField>(
        find.byKey(const ValueKey('skill-name-field')),
      );
      expect(nameField.controller?.text, '');

      // Save button should be disabled initially
      final saveBtn = tester.widget<FilledButton>(
        find.byKey(const ValueKey('save-btn')),
      );
      expect(saveBtn.onPressed, isNull);
    });

    // ── Requirement 4 ───────────────────────────────────────────────────────
    testWidgets(
        'navigating to /library/skills/:id displays skill editor in edit mode for given ID',
        (tester) async {
      await tester.pumpWidget(
        _buildRouterApp(initialLocation: '/library/skills/sk-1'),
      );
      await tester.pumpAndSettle();

      // SkillEditorScreen in edit mode
      expect(find.byType(SkillEditorScreen), findsOneWidget);

      final nameField = tester.widget<TextField>(
        find.byKey(const ValueKey('skill-name-field')),
      );
      expect(nameField.controller?.text, 'Code Reviewer');

      final descField = tester.widget<TextField>(
        find.byKey(const ValueKey('skill-desc-field')),
      );
      expect(descField.controller?.text, 'Reviews pull requests.');

      final contentField = tester.widget<TextField>(
        find.byKey(const ValueKey('skill-content-field')),
      );
      expect(contentField.controller?.text, '## Review guidelines');
    });

    // ── Requirement 5 ───────────────────────────────────────────────────────
    group('all library tabs handle live data, empty states, and errors seamlessly', () {
      testWidgets('renders live data across all 3 tabs', (tester) async {
        await tester.pumpWidget(_buildLibraryScreen());
        await tester.pumpAndSettle();

        // 1. Skills tab
        expect(find.byType(SkillCard), findsNWidgets(2));
        expect(find.text('Code Reviewer'), findsOneWidget);
        expect(find.text('Triage Bot'), findsOneWidget);

        // 2. MCPs tab
        await tester.tap(find.widgetWithText(Tab, 'MCPs'));
        await tester.pumpAndSettle();
        expect(find.byType(MCPCard), findsNWidgets(2));
        expect(find.text('Repo Server'), findsOneWidget);
        expect(find.text('Local Files'), findsOneWidget);

        // 3. Agents tab
        await tester.tap(find.widgetWithText(Tab, 'Agents'));
        await tester.pumpAndSettle();
        expect(find.byType(AgentCard), findsNWidgets(2));
        expect(find.text('PR Review Bot'), findsOneWidget);
        expect(find.text('Support Agent'), findsOneWidget);
      });

      testWidgets('renders empty states across all 3 tabs', (tester) async {
        await tester.pumpWidget(
          _buildLibraryScreen(
            skills: const [],
            mcps: const [],
            agents: const [],
          ),
        );
        await tester.pumpAndSettle();

        // 1. Skills empty state
        expect(find.byType(EmptyState), findsOneWidget);
        expect(find.text('No skills yet'), findsOneWidget);

        // 2. MCPs empty state
        await tester.tap(find.widgetWithText(Tab, 'MCPs'));
        await tester.pumpAndSettle();
        expect(find.byType(EmptyState), findsOneWidget);
        expect(find.text('No MCP connections'), findsOneWidget);

        // 3. Agents empty state
        await tester.tap(find.widgetWithText(Tab, 'Agents'));
        await tester.pumpAndSettle();
        expect(find.byType(EmptyState), findsOneWidget);
        expect(find.text('No agents yet'), findsOneWidget);
      });

      testWidgets('renders error states across all 3 tabs', (tester) async {
        final errorApp = ProviderScope(
          overrides: [
            skillsProvider.overrideWith(
              () => _ErrorSkillsNotifier('network_skill_fail'),
            ),
            mcpsProvider.overrideWith(
              () => _ErrorMcpsNotifier('network_mcp_fail'),
            ),
            agentsProvider.overrideWith(
              () => _ErrorAgentsNotifier('network_agent_fail'),
            ),
          ],
          child: MaterialApp(
            theme: LoomTheme.light,
            home: const LibraryScreen(),
          ),
        );

        await tester.pumpWidget(errorApp);
        await tester.pumpAndSettle();

        // 1. Skills error state
        expect(find.textContaining('Failed to load skills'), findsOneWidget);

        // 2. MCPs error state
        await tester.tap(find.widgetWithText(Tab, 'MCPs'));
        await tester.pumpAndSettle();
        expect(
          find.textContaining('Failed to load MCP connections'),
          findsOneWidget,
        );

        // 3. Agents error state
        await tester.tap(find.widgetWithText(Tab, 'Agents'));
        await tester.pumpAndSettle();
        expect(find.textContaining('Failed to load agents'), findsOneWidget);
      });
    });

    // ── Integration: Navigation from SkillsTabView to Editor and Back ──────
    testWidgets('New Skill button pushes editor and back button returns to library',
        (tester) async {
      await tester.pumpWidget(
        _buildRouterApp(initialLocation: '/library'),
      );
      await tester.pumpAndSettle();

      expect(find.byType(LibraryScreen), findsOneWidget);
      expect(find.byType(SkillEditorScreen), findsNothing);

      // Tap New Skill button
      await tester.tap(find.byKey(const ValueKey('new-skill-btn')));
      await tester.pumpAndSettle();

      // SkillEditorScreen is now visible
      expect(find.byType(SkillEditorScreen), findsOneWidget);
      expect(find.text('New Skill'), findsOneWidget);

      // Tap Back button in editor
      await tester.tap(find.byKey(const ValueKey('editor-back-btn')));
      await tester.pumpAndSettle();

      // Back on LibraryScreen
      expect(find.byType(LibraryScreen), findsOneWidget);
      expect(find.byType(SkillEditorScreen), findsNothing);
    });
  });
}
