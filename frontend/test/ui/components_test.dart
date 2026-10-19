import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loom_ui/models/agent_definition.dart';
import 'package:loom_ui/models/mcp_connection.dart';
import 'package:loom_ui/models/session.dart';
import 'package:loom_ui/models/skill.dart';
import 'package:loom_ui/ui/components/agent_card.dart';
import 'package:loom_ui/ui/components/confirm_dialog.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/components/loom_button.dart';
import 'package:loom_ui/ui/components/loom_text_field.dart';
import 'package:loom_ui/ui/components/mcp_card.dart';
import 'package:loom_ui/ui/components/session_row.dart';
import 'package:loom_ui/ui/components/skill_card.dart';
import 'package:loom_ui/ui/components/stat_card.dart';
import 'package:loom_ui/ui/components/tag_chip_input.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

Widget _wrapWithTheme(Widget child, {ThemeData? theme}) {
  return MaterialApp(
    theme: theme ?? LoomTheme.light,
    home: Scaffold(body: child),
  );
}

void main() {
  group('LoomButton', () {
    testWidgets('renders label and handles tap', (tester) async {
      var pressed = false;
      await tester.pumpWidget(
        _wrapWithTheme(
          LoomButton(
            label: 'Submit',
            onPressed: () => pressed = true,
          ),
        ),
      );

      expect(find.text('Submit'), findsOneWidget);
      await tester.tap(find.text('Submit'));
      await tester.pump();
      expect(pressed, isTrue);
    });

    testWidgets('shows loading spinner when loading is true', (tester) async {
      await tester.pumpWidget(
        _wrapWithTheme(
          const LoomButton(
            label: 'Submit',
            loading: true,
          ),
        ),
      );

      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Submit'), findsNothing);
    });

    testWidgets('does not fire onPressed when loading is true', (tester) async {
      var pressed = false;
      await tester.pumpWidget(
        _wrapWithTheme(
          LoomButton(
            label: 'Loading',
            loading: true,
            onPressed: () => pressed = true,
          ),
        ),
      );

      await tester.tap(find.byType(CircularProgressIndicator));
      await tester.pump();
      expect(pressed, isFalse);
    });
  });

  group('LoomTextField', () {
    testWidgets('renders label, hint and triggers onChanged', (tester) async {
      String? entered;
      await tester.pumpWidget(
        _wrapWithTheme(
          LoomTextField(
            label: 'API Key',
            hint: 'sk-...',
            onChanged: (val) => entered = val,
          ),
        ),
      );

      expect(find.text('API Key'), findsOneWidget);
      expect(find.text('sk-...'), findsOneWidget);

      await tester.enterText(find.byType(TextField), 'secret-123');
      await tester.pump();
      expect(entered, 'secret-123');
    });

    testWidgets('displays error text', (tester) async {
      await tester.pumpWidget(
        _wrapWithTheme(
          const LoomTextField(
            label: 'API Key',
            errorText: 'Invalid key',
          ),
        ),
      );

      expect(find.text('Invalid key'), findsOneWidget);
    });

    testWidgets('toggles obscure password visibility', (tester) async {
      await tester.pumpWidget(
        _wrapWithTheme(
          const LoomTextField(
            label: 'Password',
            obscure: true,
          ),
        ),
      );

      final iconButton = find.byType(IconButton);
      expect(iconButton, findsOneWidget);

      // Initially obscured (visibility icon)
      expect(find.byIcon(Icons.visibility_off), findsOneWidget);

      await tester.tap(iconButton);
      await tester.pump();
      expect(find.byIcon(Icons.visibility), findsOneWidget);
    });
  });

  group('EmptyState', () {
    testWidgets('renders icon, headline, body, and action button', (tester) async {
      var actionCalled = false;
      await tester.pumpWidget(
        _wrapWithTheme(
          EmptyState(
            icon: Icons.inbox,
            headline: 'No items yet',
            body: 'Create your first item to get started.',
            actionLabel: 'Create Now',
            onAction: () => actionCalled = true,
          ),
        ),
      );

      expect(find.byIcon(Icons.inbox), findsOneWidget);
      expect(find.text('No items yet'), findsOneWidget);
      expect(find.text('Create your first item to get started.'), findsOneWidget);
      expect(find.text('Create Now'), findsOneWidget);

      await tester.tap(find.text('Create Now'));
      await tester.pump();
      expect(actionCalled, isTrue);
    });
  });

  group('StatCard', () {
    testWidgets('renders numeric count and label', (tester) async {
      await tester.pumpWidget(
        _wrapWithTheme(
          const StatCard(
            label: 'Active Agents',
            count: 42,
          ),
        ),
      );

      expect(find.text('Active Agents'), findsOneWidget);
      expect(find.text('42'), findsOneWidget);
    });

    testWidgets('renders skeleton loader when loading', (tester) async {
      await tester.pumpWidget(
        _wrapWithTheme(
          const StatCard(
            label: 'Active Agents',
            loading: true,
          ),
        ),
      );

      expect(find.byType(SkeletonLoader), findsWidgets);
    });

    testWidgets('renders error state with retry action', (tester) async {
      var retried = false;
      await tester.pumpWidget(
        _wrapWithTheme(
          StatCard(
            label: 'Active Agents',
            hasError: true,
            onRetry: () => retried = true,
          ),
        ),
      );

      expect(find.text("Couldn't load"), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);

      await tester.tap(find.text('Retry'));
      await tester.pump();
      expect(retried, isTrue);
    });
  });

  group('SessionRow', () {
    testWidgets('renders session status and workflow name', (tester) async {
      final session = Session(
        id: 's-1',
        workspaceId: 'ws-1',
        workflowDefinitionId: 'wf-1',
        status: SessionStatus.running,
        startedAt: DateTime.now().millisecondsSinceEpoch - 10000,
        agentCount: 2,
      );

      await tester.pumpWidget(
        _wrapWithTheme(
          SessionRow(
            session: session,
            workflowName: 'PR Review Workflow',
          ),
        ),
      );

      expect(find.text('running'), findsOneWidget);
      expect(find.text('PR Review Workflow'), findsOneWidget);
      expect(find.text('2 agents'), findsOneWidget);
    });
  });

  // ---------------------------------------------------------------------------
  // Task-03 widget tests
  // ---------------------------------------------------------------------------

  group('TagChipInput', () {
    testWidgets('adds a tag on Enter and fires onChanged', (tester) async {
      List<String> latest = [];
      await tester.pumpWidget(
        _wrapWithTheme(
          TagChipInput(
            initialTags: const [],
            onChanged: (tags) => latest = tags,
          ),
        ),
      );

      await tester.enterText(find.byKey(const ValueKey('tag-input')), 'dart');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pump();

      expect(find.text('dart'), findsOneWidget);
      expect(latest, contains('dart'));
    });

    testWidgets('removes a tag on delete icon tap and fires onChanged',
        (tester) async {
      List<String> latest = ['flutter'];
      await tester.pumpWidget(
        _wrapWithTheme(
          TagChipInput(
            initialTags: const ['flutter'],
            onChanged: (tags) => latest = tags,
          ),
        ),
      );

      expect(find.text('flutter'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('tag-delete-0')));
      await tester.pump();

      expect(find.text('flutter'), findsNothing);
      expect(latest, isEmpty);
    });
  });

  group('ConfirmDialog', () {
    testWidgets('returns true on confirm tap', (tester) async {
      bool? result;
      await tester.pumpWidget(
        _wrapWithTheme(
          Builder(
            builder: (ctx) => LoomButton(
              label: 'Open',
              onPressed: () async {
                result = await ConfirmDialog.show(
                  ctx,
                  title: 'Delete item?',
                  message: 'This cannot be undone.',
                  confirmLabel: 'Delete',
                  isDestructive: true,
                );
              },
            ),
          ),
        ),
      );

      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();

      // Dialog is showing — tap Confirm
      expect(find.text('Delete item?'), findsOneWidget);
      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();

      expect(result, isTrue);
    });

    testWidgets('returns false on cancel tap', (tester) async {
      bool? result;
      await tester.pumpWidget(
        _wrapWithTheme(
          Builder(
            builder: (ctx) => LoomButton(
              label: 'Open',
              onPressed: () async {
                result = await ConfirmDialog.show(
                  ctx,
                  title: 'Delete item?',
                  message: 'This cannot be undone.',
                );
              },
            ),
          ),
        ),
      );

      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();

      expect(find.text('Delete item?'), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();

      expect(result, isFalse);
    });
  });

  group('SkillCard', () {
    const skill = Skill(
      id: 'sk-1',
      name: 'Code Reviewer',
      description: 'Reviews pull requests for correctness and style.',
      tags: ['git', 'review'],
      createdAt: 0,
      updatedAt: 0,
    );

    testWidgets('displays name, description, tags, triggers onTap and overflow actions',
        (tester) async {
      var tapped = false;
      var edited = false;
      var deleted = false;

      await tester.pumpWidget(
        _wrapWithTheme(
          SkillCard(
            skill: skill,
            onTap: () => tapped = true,
            onEdit: () => edited = true,
            onDelete: () => deleted = true,
          ),
        ),
      );

      // Verify content
      expect(find.text('Code Reviewer'), findsOneWidget);
      expect(find.text('Reviews pull requests for correctness and style.'),
          findsOneWidget);
      expect(find.text('git'), findsOneWidget);
      expect(find.text('review'), findsOneWidget);

      // onTap
      await tester.tap(find.text('Code Reviewer'));
      await tester.pump();
      expect(tapped, isTrue);

      // Edit via overflow menu
      await tester.tap(find.byIcon(Icons.more_vert));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('skill-edit')));
      await tester.pumpAndSettle();
      expect(edited, isTrue);

      // Delete via overflow menu
      await tester.tap(find.byIcon(Icons.more_vert));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('skill-delete')));
      await tester.pumpAndSettle();
      expect(deleted, isTrue);
    });
  });

  group('MCPCard', () {
    testWidgets('renders green dot for CONNECTED', (tester) async {
      const mcp = MCPConnection(
        id: 'mcp-1',
        name: 'GitHub MCP',
        type: 'github',
        status: MCPStatus.CONNECTED,
        createdAt: 0,
      );

      await tester.pumpWidget(
        _wrapWithTheme(
          MCPCard(
            mcp: mcp,
            onDelete: () {},
          ),
        ),
      );

      final dot = tester.widget<Container>(
        find.byKey(const ValueKey('mcp-status-dot')),
      );
      final decoration = dot.decoration as BoxDecoration;
      expect(decoration.color, equals(LoomColors.light.success));
    });

    testWidgets('renders grey dot for DISCONNECTED', (tester) async {
      const mcp = MCPConnection(
        id: 'mcp-2',
        name: 'Jira MCP',
        type: 'jira',
        status: MCPStatus.DISCONNECTED,
        createdAt: 0,
      );

      await tester.pumpWidget(
        _wrapWithTheme(
          MCPCard(
            mcp: mcp,
            onDelete: () {},
          ),
        ),
      );

      final dot = tester.widget<Container>(
        find.byKey(const ValueKey('mcp-status-dot')),
      );
      final decoration = dot.decoration as BoxDecoration;
      expect(decoration.color, equals(LoomColors.light.ink3));
    });
  });

  group('AgentCard', () {
    const agent = AgentDefinition(
      id: 'ag-1',
      name: 'Reviewer Agent',
      roleDescription: 'Reviews code and suggests improvements.',
      skillId: 'sk-1',
      allowedMcpIds: ['mcp-1', 'mcp-2'],
      createdAt: 0,
      updatedAt: 0,
    );

    testWidgets('displays agent name, role, skill name, and MCP list', (tester) async {
      await tester.pumpWidget(
        _wrapWithTheme(
          AgentCard(
            agent: agent,
            skillName: 'Code Reviewer',
            mcpNames: const ['GitHub', 'Jira'],
            onTap: () {},
            onEdit: () {},
            onDelete: () {},
          ),
        ),
      );

      expect(find.text('Reviewer Agent'), findsOneWidget);
      expect(find.text('Reviews code and suggests improvements.'), findsOneWidget);
      expect(find.text('Code Reviewer'), findsOneWidget);
      expect(find.text('GitHub'), findsOneWidget);
      expect(find.text('Jira'), findsOneWidget);
    });
  });
}
