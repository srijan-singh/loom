import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:loom_ui/router/router.dart';
import 'package:loom_ui/services/engine_process_service.dart';
import 'package:loom_ui/services/storage_service.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';
import 'package:loom_ui/ui/screens/onboarding/welcome_screen.dart';
import 'package:patrol/patrol.dart';
import 'package:shared_preferences/shared_preferences.dart';

class MockSecureStorage extends Fake implements FlutterSecureStorage {
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
    if (value != null) {
      _storage[key] = value;
    }
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
  }) async {
    return _storage[key];
  }

  @override
  Future<void> delete({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    _storage.remove(key);
  }
}

void main() {
  patrolTest(
    'Smoke test app navigation and onboarding welcome screen with Patrol',
    ($) async {
      SharedPreferences.setMockInitialValues({'loom_onboarding_done': false});
      final prefs = await SharedPreferences.getInstance();
      final storage = StorageService(MockSecureStorage(), prefs);
      final engine = EngineProcessService();

      await $.pumpWidgetAndSettle(
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

      // Verify Welcome Screen Elements
      expect($('Loom'), findsOneWidget);
      expect($('Your AI workforce, on your machine'), findsOneWidget);
    },
  );
}
