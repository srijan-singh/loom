import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:integration_test/integration_test.dart';
import 'package:loom_ui/router/router.dart';
import 'package:loom_ui/services/api_client.dart';
import 'package:loom_ui/services/engine_process_service.dart';
import 'package:loom_ui/services/storage_service.dart';
import 'package:loom_ui/ui/screens/onboarding/welcome_screen.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';
import 'package:shared_preferences/shared_preferences.dart';

// ---------------------------------------------------------------------------
// Minimal in-memory secure storage used for widget pump — no platform channels.
// ---------------------------------------------------------------------------

class _MockSecureStorage extends Fake implements FlutterSecureStorage {
  final Map<String, String> _storage = {};

  @override
  Future<void> write({
    required String key,
    required String? value,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (value != null) _storage[key] = value;
  }

  @override
  Future<String?> read({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async =>
      _storage[key];

  @override
  Future<void> delete({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async =>
      _storage.remove(key);
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// Reads --dart-define values injected by CI (or returns empty string locally).
const String _engineUrl =
    String.fromEnvironment('LOOM_ENGINE_URL', defaultValue: '');
const String _engineToken =
    String.fromEnvironment('LOOM_TOKEN', defaultValue: '');

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  // ── Test 1: loom-engine reachability ───────────────────────────────────────
  //
  // Hits GET /health on the live engine booted by CI.
  // Skipped gracefully when LOOM_ENGINE_URL is not provided (local runs without
  // a running engine).
  test(
    'loom-engine /health returns 200',
    skip: _engineUrl.isEmpty ? 'LOOM_ENGINE_URL not set — no engine available' : false,
    () async {
    final response = await http
        .get(
          Uri.parse('$_engineUrl/health'),
          headers: {
            if (_engineToken.isNotEmpty) 'X-Loom-Token': _engineToken,
          },
        )
        .timeout(const Duration(seconds: 10));

    expect(
      response.statusCode,
      200,
      reason:
          'loom-engine /health must return 200. '
          'Engine URL: $_engineUrl — check /tmp/loom.log on the runner.',
    );
  });

  // ── Test 2: Flutter app renders the onboarding welcome screen ──────────────
  //
  // Pure widget test — no device, no platform channels, no engine process.
  // Verifies the Flutter app boots without crashing and renders its initial UI.
  testWidgets('Welcome screen renders core UI elements', (tester) async {
    SharedPreferences.setMockInitialValues({'loom_onboarding_done': false});
    final prefs = await SharedPreferences.getInstance();
    final storage = StorageService(_MockSecureStorage(), prefs);
    final engine = EngineProcessService();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          storageServiceProvider.overrideWithValue(storage),
          engineProcessServiceProvider.overrideWithValue(engine),
        ],
        child: MaterialApp.router(
          title: 'Loom',
          debugShowCheckedModeBanner: false,
          theme: LoomTheme.light,
          darkTheme: LoomTheme.dark,
          themeMode: ThemeMode.light,
          routerConfig: GoRouter(
            initialLocation: Routes.onboardingWelcome,
            routes: [
              GoRoute(
                path: Routes.onboardingWelcome,
                builder: (_, __) => const OnboardingWelcomeScreen(),
              ),
            ],
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Loom'), findsOneWidget);
    expect(find.text('Your AI workforce, on your machine'), findsOneWidget);
  });
}
