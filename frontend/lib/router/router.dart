import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:loom_ui/services/storage_service.dart';
import 'package:loom_ui/ui/screens/app_shell.dart';
import 'package:loom_ui/ui/screens/dashboard/dashboard_screen.dart';
import 'package:loom_ui/ui/screens/library_screen.dart';
import 'package:loom_ui/ui/screens/onboarding/api_key_screen.dart';
import 'package:loom_ui/ui/screens/onboarding/welcome_screen.dart';
import 'package:loom_ui/ui/screens/settings_screen.dart';
import 'package:loom_ui/ui/screens/workspaces_screen.dart';

/// All route constants in one place.
abstract class Routes {
  static const onboardingWelcome = '/onboarding/welcome';
  static const onboardingApiKey = '/onboarding/api-key';
  static const onboardingMcpSetup = '/onboarding/mcp-setup';
  static const dashboard = '/dashboard';
  static const library = '/library';
  static const workspaces = '/workspaces';
  static const workspacesNew = '/workspaces/new';
  static const templates = '/templates';
  static const settings = '/settings';
  static const sessions = '/sessions';
}

// ---------------------------------------------------------------------------
// Providers
// ---------------------------------------------------------------------------

final storageServiceProvider = Provider<StorageService>((ref) {
  throw UnimplementedError('Override in ProviderScope');
});

final routerProvider = Provider<GoRouter>((ref) {
  final storage = ref.watch(storageServiceProvider);

  return GoRouter(
    initialLocation: Routes.dashboard,
    redirect: (context, state) {
      final onboarded = true; //storage.isOnboardingComplete(); CHANGE IT only for testing
      final inOnboarding = state.uri.path.startsWith('/onboarding');

      if (!onboarded && !inOnboarding) {
        return Routes.onboardingWelcome;
      }
      if (onboarded && inOnboarding) {
        return Routes.dashboard;
      }
      return null;
    },
    routes: [
      // ------------------------------------------------------------------
      // Onboarding (no shell)
      // ------------------------------------------------------------------
      GoRoute(
        path: Routes.onboardingWelcome,
        name: 'onboarding-welcome',
        builder: (_, __) => const OnboardingWelcomeScreen(),
      ),
      GoRoute(
        path: Routes.onboardingApiKey,
        name: 'onboarding-api-key',
        builder: (_, __) => const OnboardingApiKeyScreen(),
      ),
      GoRoute(
        path: Routes.onboardingMcpSetup,
        name: 'onboarding-mcp-setup',
        // Stub: skip to dashboard until MCP setup screen is built.
        redirect: (_, __) => Routes.dashboard,
        builder: (_, __) => const OnboardingWelcomeScreen(),
      ),

      // ------------------------------------------------------------------
      // Main app shell
      // ------------------------------------------------------------------
      ShellRoute(
        builder: (_, state, child) => AppShell(child: child),
        routes: [
          GoRoute(
            path: Routes.dashboard,
            name: 'dashboard',
            builder: (_, __) => const DashboardScreen(),
          ),
          GoRoute(
            path: Routes.library,
            name: 'library',
            builder: (_, __) => const LibraryScreen(),
          ),
          GoRoute(
            path: Routes.workspaces,
            name: 'workspaces',
            builder: (_, __) => const WorkspacesScreen(),
          ),
          GoRoute(
            path: Routes.workspacesNew,
            name: 'workspaces-new',
            builder: (_, __) => const WorkspacesScreen(),
          ),
          GoRoute(
            path: Routes.templates,
            name: 'templates',
            builder: (_, __) => const LibraryScreen(),
          ),
          GoRoute(
            path: Routes.settings,
            name: 'settings',
            builder: (_, __) => const SettingsScreen(),
          ),
          GoRoute(
            path: '${Routes.sessions}/:id',
            name: 'session-detail',
            builder: (_, __) => const DashboardScreen(),
          ),
        ],
      ),
    ],
  );
});
