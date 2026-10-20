import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:loom_ui/models/llm_connection.dart';
import 'package:loom_ui/providers/llm_providers.dart';
import 'package:loom_ui/router/router.dart';
import 'package:loom_ui/services/api_client.dart';
import 'package:loom_ui/services/engine_process_service.dart';
import 'package:loom_ui/services/storage_service.dart';
import 'package:loom_ui/ui/components/loom_button.dart';
import 'package:loom_ui/ui/screens/onboarding/api_key_screen.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';
import 'package:shared_preferences/shared_preferences.dart';

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

class _FakeLlmConnectionsNotifier extends LlmConnectionsNotifier {
  final List<LlmConnection> _connections;
  final bool shouldThrow;
  LlmConnection? lastCreated;
  // Store the draft apiKey before the fake replaces it
  String? lastDraftApiKey;

  _FakeLlmConnectionsNotifier({
    this.shouldThrow = false,
    List<LlmConnection> connections = const [],
  }) : _connections = List.from(connections);

  @override
  Future<List<LlmConnection>> build() async => List.from(_connections);

  @override
  Future<LlmConnection> create({
    required String name,
    required String baseUrl,
    required String model,
    required String apiKey,
    bool isDefault = false,
  }) async {
    if (shouldThrow) throw Exception('Create failed');
    lastDraftApiKey = apiKey;
    final conn = LlmConnection(
      id: 'new-id',
      name: name,
      baseUrl: baseUrl,
      model: model,
      apiKey: apiKey, // use the actual draft key for testing
      isDefault: isDefault,
      createdAt: 0,
    );
    lastCreated = conn;
    return conn;
  }
}

class _FakeApiClient extends ApiClient {
  _FakeApiClient() : super(EngineProcessService());

  bool testShouldSucceed = true;
  String? testErrorMessage;

  @override
  Future<void> testLlmConnection({
    required String baseUrl,
    required String apiKey,
    required String model,
  }) async {
    if (!testShouldSucceed) {
      throw LlmTestException(
        LlmTestFailure.rejected,
        message: testErrorMessage ?? 'Rejected',
      );
    }
  }
}

class _FakeStorageService extends StorageService {
  bool completed = false;

  _FakeStorageService(super.prefs);

  @override
  Future<void> completeOnboarding() async {
    completed = true;
  }
}

// ---------------------------------------------------------------------------
// Helper to build the widget under test
// ---------------------------------------------------------------------------

Future<_FakeLlmConnectionsNotifier> _buildScreen(
  WidgetTester tester, {
  bool testShouldSucceed = true,
  String? testErrorMessage,
  bool createShouldThrow = false,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final storage = _FakeStorageService(prefs);
  final llmNotifier = _FakeLlmConnectionsNotifier(
    shouldThrow: createShouldThrow,
  );
  final fakeClient = _FakeApiClient()
    ..testShouldSucceed = testShouldSucceed
    ..testErrorMessage = testErrorMessage;

  // Wide surface to avoid LoomButton font overflow in test environment
  tester.view.physicalSize = const Size(1200, 1000);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        storageServiceProvider.overrideWithValue(storage),
        apiClientProvider.overrideWithValue(fakeClient),
        llmConnectionsProvider.overrideWith(() => llmNotifier),
      ],
      child: MaterialApp.router(
        theme: LoomTheme.light,
        routerConfig: GoRouter(
          initialLocation: '/onboarding/api-key',
          routes: [
            GoRoute(
              path: '/onboarding/api-key',
              builder: (_, __) => const OnboardingApiKeyScreen(),
            ),
            GoRoute(
              path: '/onboarding/welcome',
              builder: (_, __) =>
                  const Scaffold(body: Text('Welcome')),
            ),
            GoRoute(
              path: '/dashboard',
              builder: (_, __) =>
                  const Scaffold(body: Text('Dashboard')),
            ),
          ],
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return llmNotifier;
}

// Helper: read the text of a TextField inside a LoomTextField keyed with [key]
String _loomFieldText(WidgetTester tester, Key key) {
  final tf = tester.widget<TextField>(
    find.descendant(
      of: find.byKey(key),
      matching: find.byType(TextField),
    ),
  );
  return tf.controller?.text ?? '';
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('OnboardingApiKeyScreen', () {
    testWidgets('renders provider-neutral heading and key fields',
        (tester) async {
      await _buildScreen(tester);

      // No Claude/GPT toggle
      expect(find.text('Claude (Anthropic)'), findsNothing);
      expect(find.text('GPT (OpenAI)'), findsNothing);

      // New heading
      expect(find.text('Connect an AI model'), findsOneWidget);
      expect(find.text('Step 1 of 2'), findsOneWidget);

      // Form fields present
      expect(find.byKey(const ValueKey('llm-name-field')), findsOneWidget);
      expect(find.byKey(const ValueKey('llm-base-url-field')), findsOneWidget);
      expect(find.byKey(const ValueKey('llm-api-key-field')), findsOneWidget);
      expect(find.byKey(const ValueKey('llm-model-field')), findsOneWidget);
    });

    testWidgets('shows preset chips', (tester) async {
      await _buildScreen(tester);

      expect(find.byKey(const ValueKey('preset-OpenAI')), findsOneWidget);
      expect(find.byKey(const ValueKey('preset-OpenRouter')), findsOneWidget);
      expect(find.byKey(const ValueKey('preset-Groq')), findsOneWidget);
      expect(find.byKey(const ValueKey('preset-Ollama (local)')),
          findsOneWidget);
      expect(find.byKey(const ValueKey('preset-LM Studio (local)')),
          findsOneWidget);
      expect(find.byKey(const ValueKey('preset-Custom')), findsOneWidget);
    });

    testWidgets('selecting OpenAI preset prefills Name and Base URL',
        (tester) async {
      await _buildScreen(tester);

      await tester.tap(find.byKey(const ValueKey('preset-OpenAI')));
      await tester.pumpAndSettle();

      expect(
        _loomFieldText(tester, const ValueKey('llm-name-field')),
        'OpenAI',
      );
      expect(
        _loomFieldText(tester, const ValueKey('llm-base-url-field')),
        'https://api.openai.com/v1',
      );
    });

    testWidgets('selecting Custom preset leaves name/baseUrl editable (empty)',
        (tester) async {
      await _buildScreen(tester);

      await tester.tap(find.byKey(const ValueKey('preset-Custom')));
      await tester.pumpAndSettle();

      // Custom preset does NOT prefill values
      expect(
        _loomFieldText(tester, const ValueKey('llm-name-field')),
        isEmpty,
      );
    });

    testWidgets('any model ID is accepted in the model field', (tester) async {
      await _buildScreen(tester);

      await tester.enterText(
          find.descendant(
              of: find.byKey(const ValueKey('llm-model-field')),
              matching: find.byType(TextField)),
          'my-custom-model-v2');
      await tester.pump();

      expect(
        _loomFieldText(tester, const ValueKey('llm-model-field')),
        'my-custom-model-v2',
      );
    });

    testWidgets('Ollama local preset allows empty API key on submit',
        (tester) async {
      final notifier = await _buildScreen(tester);

      // Select Ollama local
      await tester.tap(find.byKey(const ValueKey('preset-Ollama (local)')));
      await tester.pumpAndSettle();

      // Enter model (required)
      await tester.enterText(
          find.descendant(
              of: find.byKey(const ValueKey('llm-model-field')),
              matching: find.byType(TextField)),
          'llama3');
      await tester.pump();

      // Leave API key empty and run test
      await tester.tap(find.byKey(const ValueKey('llm-test-btn')));
      await tester.pumpAndSettle();

      // Test should succeed (fakeClient returns ok)
      expect(find.text('Connection verified.'), findsOneWidget);

      // Save
      await tester.tap(find.byKey(const ValueKey('llm-submit-btn')));
      await tester.pumpAndSettle();

      // The submitted API key should be the non-blank placeholder
      expect(notifier.lastDraftApiKey, 'not-needed');
    });

    testWidgets('invalid input shows inline errors and blocks API calls',
        (tester) async {
      await _buildScreen(tester);

      // Switch to Custom so name and base URL fields are empty
      await tester.tap(find.byKey(const ValueKey('preset-Custom')));
      await tester.pumpAndSettle();

      // Ensure all fields are clear
      await tester.enterText(
          find.descendant(
              of: find.byKey(const ValueKey('llm-name-field')),
              matching: find.byType(TextField)),
          '');
      await tester.enterText(
          find.descendant(
              of: find.byKey(const ValueKey('llm-base-url-field')),
              matching: find.byType(TextField)),
          '');
      await tester.enterText(
          find.descendant(
              of: find.byKey(const ValueKey('llm-model-field')),
              matching: find.byType(TextField)),
          '');
      await tester.pump();

      // Try to test without filling required fields
      await tester.tap(find.byKey(const ValueKey('llm-test-btn')));
      await tester.pumpAndSettle();

      // Inline errors appear
      expect(find.text('Name is required.'), findsOneWidget);
      expect(find.text('Base URL must start with http:// or https://.'),
          findsOneWidget);
      expect(find.text('Model ID is required.'), findsOneWidget);
      expect(
          find.text('API key is required for this provider.'), findsOneWidget);
    });

    testWidgets('changing a field after successful test resets test status',
        (tester) async {
      await _buildScreen(tester);

      // Fill in required fields (OpenAI preset already fills name + URL)
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

      // Run test successfully
      await tester.tap(find.byKey(const ValueKey('llm-test-btn')));
      await tester.pumpAndSettle();
      expect(find.text('Connection verified.'), findsOneWidget);

      // Change model field
      await tester.enterText(
          find.descendant(
              of: find.byKey(const ValueKey('llm-model-field')),
              matching: find.byType(TextField)),
          'gpt-4o-mini');
      await tester.pump();

      // Test status should be reset (no success indicator)
      expect(find.text('Connection verified.'), findsNothing);
    });

    testWidgets('failed test shows backend error text and blocks Save',
        (tester) async {
      await _buildScreen(tester,
          testShouldSucceed: false,
          testErrorMessage: 'Invalid credentials');

      // Fill required fields
      await tester.enterText(
          find.descendant(
              of: find.byKey(const ValueKey('llm-model-field')),
              matching: find.byType(TextField)),
          'gpt-4o');
      await tester.enterText(
          find.descendant(
              of: find.byKey(const ValueKey('llm-api-key-field')),
              matching: find.byType(TextField)),
          'bad-key');
      await tester.pump();

      await tester.tap(find.byKey(const ValueKey('llm-test-btn')));
      await tester.pumpAndSettle();

      expect(find.text('Invalid credentials'), findsOneWidget);

      // Submit button should be disabled (test state is not ok)
      final submitBtnWidget = tester.widget<LoomButton>(
        find.byKey(const ValueKey('llm-submit-btn')),
      );
      expect(submitBtnWidget.onPressed, isNull);
    });

    testWidgets(
        'successful Save creates connection with isDefault true, completes onboarding, navigates to dashboard',
        (tester) async {
      final notifier = await _buildScreen(tester);

      // Fill in required fields
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

      // Test connection
      await tester.tap(find.byKey(const ValueKey('llm-test-btn')));
      await tester.pumpAndSettle();
      expect(find.text('Connection verified.'), findsOneWidget);

      // Save
      await tester.tap(find.byKey(const ValueKey('llm-submit-btn')));
      await tester.pumpAndSettle();

      // Check that connection was created with isDefault: true
      expect(notifier.lastCreated?.isDefault, isTrue);
      expect(notifier.lastCreated?.model, 'gpt-4o');

      // Navigated to dashboard
      expect(find.text('Dashboard'), findsOneWidget);
    });

    testWidgets('save failure shows SnackBar', (tester) async {
      await _buildScreen(tester, createShouldThrow: true);

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

      expect(find.byType(SnackBar), findsOneWidget);
      expect(find.textContaining('Failed to save connection'), findsOneWidget);
    });
  });
}
