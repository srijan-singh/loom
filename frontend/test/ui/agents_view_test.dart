import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loom_ui/models/agent_definition.dart';
import 'package:loom_ui/models/mcp_connection.dart';
import 'package:loom_ui/models/skill.dart';
import 'package:loom_ui/providers/library_providers.dart';
import 'package:loom_ui/ui/components/agent_card.dart';
import 'package:loom_ui/ui/components/confirm_dialog.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/screens/library/agent_detail_panel.dart';
import 'package:loom_ui/ui/screens/library/agents_tab_view.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

// ---------------------------------------------------------------------------
// Helpers & Fakes
// ---------------------------------------------------------------------------

class _FakeAgentsNotifier extends AgentsNotifier {
  _FakeAgentsNotifier(List<AgentDefinition> agents)
      : _agents = List.from(agents);

  final List<AgentDefinition> _agents;
  final List<AgentDefinition> createdCalls = [];
  final List<(String, AgentDefinition)> updatedCalls = [];
  final List<String> deletedCalls = [];

  @override
  Future<List<AgentDefinition>> build() async => List.from(_agents);

  @override
  Future<AgentDefinition> createAgent(AgentDefinition agent) async {
    createdCalls.add(agent);
    final created = agent.copyWith(id: 'new-agent-id');
    _agents.add(created);
    ref.invalidateSelf();
    return created;
  }

  @override
  Future<AgentDefinition> updateAgent(
      String id, AgentDefinition agent) async {
    updatedCalls.add((id, agent));
    final idx = _agents.indexWhere((a) => a.id == id);
    if (idx != -1) {
      _agents[idx] = agent;
    }
    ref.invalidateSelf();
    return agent;
  }

  @override
  Future<void> deleteAgent(String id) async {
    deletedCalls.add(id);
    _agents.removeWhere((a) => a.id == id);
    ref.invalidateSelf();
  }
}

class _FakeSkillsNotifier extends SkillsNotifier {
  _FakeSkillsNotifier(List<Skill> skills) : _skills = List.from(skills);

  final List<Skill> _skills;

  @override
  Future<List<Skill>> build() async => List.from(_skills);
}

class _FakeMcpsNotifier extends McpsNotifier {
  _FakeMcpsNotifier(List<MCPConnection> mcps) : _mcps = List.from(mcps);

  final List<MCPConnection> _mcps;

  @override
  Future<List<MCPConnection>> build() async => List.from(_mcps);
}

const _sampleSkill1 = Skill(
  id: 'sk-1',
  name: 'Code Reviewer',
  description: 'Reviews pull requests.',
  content: 'Guidelines',
  tags: ['git'],
  createdAt: 0,
  updatedAt: 0,
);

const _sampleSkill2 = Skill(
  id: 'sk-2',
  name: 'Issue Triager',
  description: 'Triages bug reports.',
  content: '',
  tags: ['bugs'],
  createdAt: 1,
  updatedAt: 1,
);

const _sampleMcp1 = MCPConnection(
  id: 'm-1',
  name: 'GitHub MCP',
  type: 'GITHUB',
  status: MCPStatus.CONNECTED,
  detail: 'github.com/acme',
  createdAt: 0,
);

const _sampleMcp2 = MCPConnection(
  id: 'm-2',
  name: 'Slack MCP',
  type: 'CUSTOM',
  status: MCPStatus.CONNECTED,
  detail: 'slack.com',
  createdAt: 1,
);

const _sampleAgent1 = AgentDefinition(
  id: 'ag-1',
  name: 'PR Review Bot',
  roleDescription: 'Automates PR reviews and checks lint.',
  skillId: 'sk-1',
  allowedMcpIds: ['m-1', 'm-2'],
  createdAt: 100,
  updatedAt: 100,
);

const _sampleAgent2 = AgentDefinition(
  id: 'ag-2',
  name: 'Support Agent',
  roleDescription: 'Answers customer support queries.',
  skillId: null,
  allowedMcpIds: ['m-1'],
  createdAt: 200,
  updatedAt: 200,
);

Widget _buildTabView({
  required _FakeAgentsNotifier agentsNotifier,
  List<Skill> skills = const [_sampleSkill1, _sampleSkill2],
  List<MCPConnection> mcps = const [_sampleMcp1, _sampleMcp2],
  void Function(AgentDefinition)? onSelectAgent,
  VoidCallback? onNewAgent,
}) {
  return ProviderScope(
    overrides: [
      agentsProvider.overrideWith(() => agentsNotifier),
      skillsProvider.overrideWith(() => _FakeSkillsNotifier(skills)),
      mcpsProvider.overrideWith(() => _FakeMcpsNotifier(mcps)),
    ],
    child: MaterialApp(
      theme: LoomTheme.light,
      home: Scaffold(
        body: AgentsTabView(
          onSelectAgent: onSelectAgent,
          onNewAgent: onNewAgent,
        ),
      ),
    ),
  );
}

Widget _buildDetailPanel({
  AgentDefinition? agent,
  required _FakeAgentsNotifier agentsNotifier,
  List<Skill> skills = const [_sampleSkill1, _sampleSkill2],
  List<MCPConnection> mcps = const [_sampleMcp1, _sampleMcp2],
  VoidCallback? onClose,
  void Function(AgentDefinition)? onSaved,
  VoidCallback? onCancel,
}) {
  return ProviderScope(
    overrides: [
      agentsProvider.overrideWith(() => agentsNotifier),
      skillsProvider.overrideWith(() => _FakeSkillsNotifier(skills)),
      mcpsProvider.overrideWith(() => _FakeMcpsNotifier(mcps)),
    ],
    child: MaterialApp(
      theme: LoomTheme.light,
      home: Scaffold(
        body: AgentDetailPanel(
          agent: agent,
          onClose: onClose,
          onSaved: onSaved,
          onCancel: onCancel,
        ),
      ),
    ),
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('AgentsTabView & AgentDetailPanel', () {
    // ── Requirement 1 ───────────────────────────────────────────────────────
    testWidgets(
        'AgentsTabView renders list of agents with assigned skill and MCP names',
        (tester) async {
      tester.view.physicalSize = const Size(1024, 768);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final notifier =
          _FakeAgentsNotifier([_sampleAgent1, _sampleAgent2]);

      await tester.pumpWidget(_buildTabView(agentsNotifier: notifier));
      await tester.pumpAndSettle();

      // Check agents are rendered
      expect(find.byType(AgentCard), findsNWidgets(2));
      expect(find.text('PR Review Bot'), findsOneWidget);
      expect(find.text('Automates PR reviews and checks lint.'),
          findsOneWidget);
      expect(find.text('Support Agent'), findsOneWidget);

      // Check resolved skill name
      expect(find.text('Code Reviewer'), findsOneWidget);

      // Check resolved MCP names
      expect(find.text('GitHub MCP'), findsNWidgets(2));
      expect(find.text('Slack MCP'), findsOneWidget);
    });

    // ── Requirement 2 ───────────────────────────────────────────────────────
    testWidgets(
        'On wide screen (>= 800px), selecting an agent opens AgentDetailPanel in the right column',
        (tester) async {
      tester.view.physicalSize = const Size(1024, 768);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final notifier =
          _FakeAgentsNotifier([_sampleAgent1, _sampleAgent2]);

      await tester.pumpWidget(_buildTabView(agentsNotifier: notifier));
      await tester.pumpAndSettle();

      // Placeholder prompt when nothing is selected
      expect(
        find.text('Select an agent to view details or create a new one.'),
        findsOneWidget,
      );
      expect(find.byType(AgentDetailPanel), findsNothing);

      // Tap on the first agent card
      await tester.tap(find.text('PR Review Bot'));
      await tester.pumpAndSettle();

      // AgentDetailPanel should be displayed inline in right column
      expect(find.byType(AgentDetailPanel), findsOneWidget);
      expect(
        tester
            .widget<TextField>(
                find.byKey(const ValueKey('agent-name-field')))
            .controller!
            .text,
        'PR Review Bot',
      );
      expect(
        tester
            .widget<TextField>(
                find.byKey(const ValueKey('agent-role-field')))
            .controller!
            .text,
        'Automates PR reviews and checks lint.',
      );
    });

    // ── Requirement 3 ───────────────────────────────────────────────────────
    testWidgets(
        'On narrow screen (< 800px), selecting an agent navigates to full screen editor',
        (tester) async {
      tester.view.physicalSize = const Size(500, 800);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final notifier =
          _FakeAgentsNotifier([_sampleAgent1, _sampleAgent2]);

      await tester.pumpWidget(_buildTabView(agentsNotifier: notifier));
      await tester.pumpAndSettle();

      // AgentDetailPanel is not in view initially
      expect(find.byType(AgentDetailPanel), findsNothing);

      // Tap an agent card
      await tester.tap(find.text('PR Review Bot'));
      await tester.pumpAndSettle();

      // Full screen route pushed with AgentDetailPanel
      expect(find.byType(AgentDetailPanel), findsOneWidget);
      expect(find.byKey(const ValueKey('agent-close-btn')), findsOneWidget);

      // Tapping close button pops the full-screen route
      await tester.tap(find.byKey(const ValueKey('agent-close-btn')));
      await tester.pumpAndSettle();

      expect(find.byType(AgentDetailPanel), findsNothing);
      expect(find.text('PR Review Bot'), findsOneWidget);
    });

    // ── Requirement 4 ───────────────────────────────────────────────────────
    testWidgets(
        'AgentDetailPanel populates Skill dropdown from live skills list',
        (tester) async {
      final notifier = _FakeAgentsNotifier([]);

      await tester.pumpWidget(
        _buildDetailPanel(
          agentsNotifier: notifier,
          skills: [_sampleSkill1, _sampleSkill2],
        ),
      );
      await tester.pumpAndSettle();

      // Find skill dropdown
      final dropdownFinder =
          find.byKey(const ValueKey('agent-skill-dropdown'));
      expect(dropdownFinder, findsOneWidget);

      // Open dropdown
      await tester.tap(dropdownFinder);
      await tester.pumpAndSettle();

      // Verify options: 'None', 'Code Reviewer', 'Issue Triager'
      expect(find.text('None').hitTestable(), findsOneWidget);
      expect(find.text('Code Reviewer').hitTestable(), findsOneWidget);
      expect(find.text('Issue Triager').hitTestable(), findsOneWidget);

      // Select 'Code Reviewer'
      await tester.tap(find.text('Code Reviewer').hitTestable());
      await tester.pumpAndSettle();

      expect(find.text('Code Reviewer'), findsOneWidget);
    });

    // ── Requirement 5 ───────────────────────────────────────────────────────
    testWidgets(
        'AgentDetailPanel populates MCP checkboxes from live MCPs list and toggles selections',
        (tester) async {
      final notifier = _FakeAgentsNotifier([]);

      await tester.pumpWidget(
        _buildDetailPanel(
          agent: _sampleAgent1, // has ['m-1', 'm-2'] selected
          agentsNotifier: notifier,
          mcps: [_sampleMcp1, _sampleMcp2],
        ),
      );
      await tester.pumpAndSettle();

      // Both MCP checkboxes should be present
      final mcp1TileFinder =
          find.byKey(const ValueKey('agent-mcp-m-1'));
      final mcp2TileFinder =
          find.byKey(const ValueKey('agent-mcp-m-2'));

      expect(mcp1TileFinder, findsOneWidget);
      expect(mcp2TileFinder, findsOneWidget);

      // Both should be checked initially for _sampleAgent1
      expect(
          tester.widget<CheckboxListTile>(mcp1TileFinder).value, isTrue);
      expect(
          tester.widget<CheckboxListTile>(mcp2TileFinder).value, isTrue);

      // Tap on mcp2 to uncheck it
      await tester.tap(mcp2TileFinder);
      await tester.pumpAndSettle();

      expect(
          tester.widget<CheckboxListTile>(mcp2TileFinder).value, isFalse);
      expect(
          tester.widget<CheckboxListTile>(mcp1TileFinder).value, isTrue);
    });

    // ── Requirement 6 ───────────────────────────────────────────────────────
    testWidgets(
        'Submitting AgentDetailPanel saves agent and updates list',
        (tester) async {
      final notifier = _FakeAgentsNotifier([]);
      AgentDefinition? savedAgent;

      await tester.pumpWidget(
        _buildDetailPanel(
          agentsNotifier: notifier,
          skills: [_sampleSkill1, _sampleSkill2],
          mcps: [_sampleMcp1, _sampleMcp2],
          onSaved: (agent) => savedAgent = agent,
        ),
      );
      await tester.pumpAndSettle();

      // Fill in name
      await tester.enterText(
          find.byKey(const ValueKey('agent-name-field')),
          'Newly Created Agent');

      // Fill in role
      await tester.enterText(
          find.byKey(const ValueKey('agent-role-field')),
          'Autonomous task handler');

      // Select skill
      await tester.tap(find.byKey(const ValueKey('agent-skill-dropdown')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Code Reviewer').hitTestable());
      await tester.pumpAndSettle();

      // Toggle an MCP checkbox
      await tester.tap(find.byKey(const ValueKey('agent-mcp-m-1')));
      await tester.pumpAndSettle();

      // Tap Save
      await tester.tap(find.byKey(const ValueKey('agent-save-btn')));
      await tester.pumpAndSettle();

      // Verify createAgent call
      expect(notifier.createdCalls.length, 1);
      final created = notifier.createdCalls.first;
      expect(created.name, 'Newly Created Agent');
      expect(created.roleDescription, 'Autonomous task handler');
      expect(created.skillId, 'sk-1');
      expect(created.allowedMcpIds, ['m-1']);

      // Callback received
      expect(savedAgent, isNotNull);
      expect(savedAgent!.name, 'Newly Created Agent');
    });

    // ── Requirement 7 ───────────────────────────────────────────────────────
    testWidgets(
        'Deleting an agent shows ConfirmDialog and deletes item on confirmation',
        (tester) async {
      tester.view.physicalSize = const Size(1024, 768);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final notifier = _FakeAgentsNotifier([_sampleAgent1]);

      await tester.pumpWidget(_buildTabView(agentsNotifier: notifier));
      await tester.pumpAndSettle();

      expect(find.byType(AgentCard), findsOneWidget);

      // Open overflow menu on AgentCard
      await tester.tap(find.byIcon(Icons.more_vert));
      await tester.pumpAndSettle();

      // Tap Delete
      await tester.tap(find.byKey(const ValueKey('agent-delete')));
      await tester.pumpAndSettle();

      // Confirm dialog appears
      expect(find.byType(ConfirmDialog), findsOneWidget);
      expect(find.text('Delete agent?'), findsOneWidget);

      // Tap Confirm 'Delete'
      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();

      // Verify deleteAgent called and item removed from list
      expect(notifier.deletedCalls, ['ag-1']);
      expect(find.byType(AgentCard), findsNothing);
      expect(find.byType(EmptyState), findsOneWidget);
    });

    // ── Additional Edge Cases ───────────────────────────────────────────────
    testWidgets('Validation prevents save when name is empty',
        (tester) async {
      final notifier = _FakeAgentsNotifier([]);

      await tester.pumpWidget(
        _buildDetailPanel(agentsNotifier: notifier),
      );
      await tester.pumpAndSettle();

      // Tap Save with empty name
      await tester.tap(find.byKey(const ValueKey('agent-save-btn')));
      await tester.pumpAndSettle();

      expect(find.text('Name is required'), findsOneWidget);
      expect(notifier.createdCalls, isEmpty);
    });

    testWidgets(
        'On wide screen, clicking + New button opens empty AgentDetailPanel in right column',
        (tester) async {
      tester.view.physicalSize = const Size(1024, 768);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final notifier = _FakeAgentsNotifier([_sampleAgent1]);

      await tester.pumpWidget(_buildTabView(agentsNotifier: notifier));
      await tester.pumpAndSettle();

      // Click "+ New"
      await tester.tap(find.byKey(const ValueKey('new-agent-btn')));
      await tester.pumpAndSettle();

      expect(find.byType(AgentDetailPanel), findsOneWidget);
      expect(
        find.descendant(
          of: find.byType(AgentDetailPanel),
          matching: find.text('New Agent'),
        ),
        findsOneWidget,
      );
      expect(
        tester
            .widget<TextField>(
                find.byKey(const ValueKey('agent-name-field')))
            .controller!
            .text,
        '',
      );
    });
  });
}
