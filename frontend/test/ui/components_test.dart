import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loom_ui/models/session.dart';
import 'package:loom_ui/ui/components/empty_state.dart';
import 'package:loom_ui/ui/components/loom_button.dart';
import 'package:loom_ui/ui/components/loom_text_field.dart';
import 'package:loom_ui/ui/components/session_row.dart';
import 'package:loom_ui/ui/components/stat_card.dart';
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
}
