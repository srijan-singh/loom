import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loom_ui/models/llm_connection.dart';
import 'package:loom_ui/providers/llm_providers.dart';
import 'package:loom_ui/services/api_client.dart';
import 'package:loom_ui/services/engine_process_service.dart';
import 'package:loom_ui/ui/components/confirm_dialog.dart';
import 'package:loom_ui/ui/components/skeleton_loader.dart';
import 'package:loom_ui/ui/screens/settings_screen.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

class _FakeLlmConnectionsNotifier extends LlmConnectionsNotifier {
  final List<LlmConnection> _connections;
  final bool shouldThrow;
  bool deleteCalled = false;
  String? deletedId;
  String? setDefaultId;
  LlmConnection? createdConnection;

  _FakeLlmConnectionsNotifier({
    required List<LlmConnection> connections,
    this.shouldThrow = false,
  }) : _connections = List.from(connections);

  @override
  Future<List<LlmConnection>> build() async {
    if (shouldThrow) throw Exception('Load failed');
    return List.from(_connections);
  }

  @override
  Future<LlmConnection> create({
    required String name,
    required String baseUrl,
    required String model,
    required String apiKey,
    bool isDefault = false,
  }) async {
    final conn = LlmConnection(
      id: 'new-id',
      name: name,
      baseUrl: baseUrl,
      model: model,
      apiKey: 'masked',
      isDefault: isDefault,
      createdAt: 0,
    );
    createdConnection = conn;
    _connections.add(conn);
    ref.invalidateSelf();
    return conn;
  }

  @override
  Future<void> delete(String id) async {
    deleteCalled = true;
    deletedId = id;
    _connections.removeWhere((c) => c.id == id);
    ref.invalidateSelf();
  }

  @override
  Future<void> setDefault(String id) async {
    setDefaultId = id;
    for (var i = 0; i < _connections.length; i++) {
      _connections[i] = _connections[i].copyWith(isDefault: _connections[i].id == id);
    }
    ref.invalidateSelf();
  }
}

class _FakeApiClient extends ApiClient {
  _FakeApiClient() : super(EngineProcessService());

  bool testShouldSucceed = true;

  @override
  Future<void> testLlmConnection({
    required String baseUrl,
    required String apiKey,
    required String model,
  }) async {
    if (!testShouldSucceed) {
      throw const LlmTestException(LlmTestFailure.rejected);
    }
  }
}

const _conn1 = LlmConnection(
  id: 'c-1',
  name: 'OpenAI',
  baseUrl: 'https://api.openai.com/v1',
  model: 'gpt-4o',
  apiKey: 'sk-****',
  isDefault: true,
  createdAt: 100,
);

const _conn2 = LlmConnection(
  id: 'c-2',
  name: 'Groq',
  baseUrl: 'https://api.groq.com/openai/v1',
  model: 'llama3-8b-8192',
  apiKey: 'gsk-****',
  isDefault: false,
  createdAt: 200,
);

// ---------------------------------------------------------------------------
// Helper
// ---------------------------------------------------------------------------

Widget _buildSettings({
  required _FakeLlmConnectionsNotifier notifier,
  _FakeApiClient? apiClient,
}) {
  return ProviderScope(
    overrides: [
      llmConnectionsProvider.overrideWith(() => notifier),
      if (apiClient != null)
        apiClientProvider.overrideWithValue(apiClient),
    ],
    child: MaterialApp(
      theme: LoomTheme.light,
      home: const Scaffold(body: SettingsScreen()),
    ),
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('SettingsScreen — Model connections', () {
    testWidgets('shows SkeletonListLoader while loading', (tester) async {
      final notifier = _FakeLlmConnectionsNotifier(
        connections: [],
        shouldThrow: false,
      );
      // Override build with a never-completing future via a separate notifier
      // class that stalls; simplest: test immediate loading state.
      // Because pumpWidget+pump(0) can catch the loading frame:
      await tester.pumpWidget(_buildSettings(notifier: notifier));
      // Immediately after pump, before pumpAndSettle, it should show loading
      expect(find.byType(SkeletonListLoader), findsOneWidget);
      await tester.pumpAndSettle();
    });

    testWidgets('shows EmptyState when no connections exist', (tester) async {
      tester.view.physicalSize = const Size(800, 700);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final notifier = _FakeLlmConnectionsNotifier(connections: []);
      await tester.pumpWidget(_buildSettings(notifier: notifier));
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('settings-empty-state')), findsOneWidget);
      expect(find.text('No model connections'), findsOneWidget);
    });

    testWidgets('shows error state when loading fails', (tester) async {
      tester.view.physicalSize = const Size(800, 700);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final notifier =
          _FakeLlmConnectionsNotifier(connections: [], shouldThrow: true);
      await tester.pumpWidget(_buildSettings(notifier: notifier));
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('settings-error-state')), findsOneWidget);
      expect(find.text('Failed to load connections'), findsOneWidget);
    });

    testWidgets('lists connections with masked key and Default badge',
        (tester) async {
      tester.view.physicalSize = const Size(1024, 768);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final notifier =
          _FakeLlmConnectionsNotifier(connections: [_conn1, _conn2]);
      await tester.pumpWidget(_buildSettings(notifier: notifier));
      await tester.pumpAndSettle();

      // Connection names
      expect(find.text('OpenAI'), findsOneWidget);
      expect(find.text('Groq'), findsOneWidget);

      // Default badge only for conn1
      expect(find.text('Default'), findsOneWidget);

      // Masked API keys shown
      expect(find.text(
              'api.openai.com · gpt-4o · sk-****'),
          findsOneWidget);
    });

    testWidgets('Set default action calls setDefault', (tester) async {
      tester.view.physicalSize = const Size(1024, 768);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final notifier =
          _FakeLlmConnectionsNotifier(connections: [_conn1, _conn2]);
      await tester.pumpWidget(_buildSettings(notifier: notifier));
      await tester.pumpAndSettle();

      // Tap Set default for conn2 (non-default)
      await tester.tap(find.text('Set default'));
      await tester.pumpAndSettle();

      expect(notifier.setDefaultId, 'c-2');
    });

    testWidgets('Delete shows ConfirmDialog and deletes on confirm',
        (tester) async {
      tester.view.physicalSize = const Size(1024, 768);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final notifier =
          _FakeLlmConnectionsNotifier(connections: [_conn1, _conn2]);
      await tester.pumpWidget(_buildSettings(notifier: notifier));
      await tester.pumpAndSettle();

      // Tap Delete for conn1
      await tester.tap(find.byKey(const ValueKey('conn-delete-c-1')));
      await tester.pumpAndSettle();

      expect(find.byType(ConfirmDialog), findsOneWidget);
      expect(find.text('Delete connection?'), findsOneWidget);

      // Tap the destructive 'Delete' button inside the dialog
      await tester.tap(find.descendant(
          of: find.byType(ConfirmDialog), matching: find.text('Delete')));
      await tester.pumpAndSettle();

      expect(notifier.deletedId, 'c-1');
    });

    testWidgets('Delete shows last-connection warning when only one remains',
        (tester) async {
      tester.view.physicalSize = const Size(1024, 768);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final notifier =
          _FakeLlmConnectionsNotifier(connections: [_conn1]);
      await tester.pumpWidget(_buildSettings(notifier: notifier));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const ValueKey('conn-delete-c-1')));
      await tester.pumpAndSettle();

      expect(find.byType(ConfirmDialog), findsOneWidget);
      expect(find.textContaining('last connection'), findsOneWidget);

      // Cancel — should not delete
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(notifier.deleteCalled, isFalse);
    });

    testWidgets('Add connection button opens dialog with LlmConnectionForm',
        (tester) async {
      // Wide surface to avoid LoomButton overflow in test font
      tester.view.physicalSize = const Size(1200, 900);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final notifier = _FakeLlmConnectionsNotifier(connections: []);
      final fakeClient = _FakeApiClient();
      await tester.pumpWidget(
          _buildSettings(notifier: notifier, apiClient: fakeClient));
      await tester.pumpAndSettle();

      // Open dialog from header button
      await tester.tap(
          find.byKey(const ValueKey('settings-add-connection-btn')));
      await tester.pumpAndSettle();

      expect(find.text('Add model connection'), findsOneWidget);
      expect(find.byKey(const ValueKey('settings-llm-form')), findsOneWidget);

      // Fill in form fields (LoomTextField wraps TextField)
      await tester.enterText(
          find.descendant(
              of: find.byKey(const ValueKey('llm-model-field')),
              matching: find.byType(TextField)),
          'gpt-4o');
      await tester.enterText(
          find.descendant(
              of: find.byKey(const ValueKey('llm-api-key-field')),
              matching: find.byType(TextField)),
          'sk-test');
      await tester.pump();

      await tester.tap(find.byKey(const ValueKey('llm-test-btn')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const ValueKey('llm-submit-btn')));
      await tester.pumpAndSettle();

      // Dialog dismissed and connection created
      expect(find.text('Add model connection'), findsNothing);
      expect(notifier.createdConnection?.model, 'gpt-4o');
    });
  });
}
