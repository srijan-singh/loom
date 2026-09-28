import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:loom_ui/router/router.dart';
import 'package:loom_ui/services/api_client.dart';
import 'package:loom_ui/services/engine_process_service.dart';
import 'package:loom_ui/services/storage_service.dart';
import 'package:loom_ui/ui/theme/loom_theme.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Resolves the path to the bundled engine JAR for the current platform.
///
/// Delegates to [EngineProcessService.resolveJarPath] so that macOS, Linux,
/// and Windows bundle layouts are all handled in one place.
String _resolveJarPath() {
  if (Platform.isMacOS) {
    // macOS: <app>.app/Contents/MacOS/<exe>  → up two levels → Resources/
    final execDir = File(Platform.resolvedExecutable).parent; // .../MacOS/
    final contentsDir = execDir.parent;                       // .../Contents/
    return '${contentsDir.path}/Resources/loom-engine.jar';
  }
  // Linux / Windows: JAR is placed next to the executable by the build.
  return EngineProcessService.resolveJarPath('loom-engine.jar');
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // --- Storage (non-sensitive flags + secure API keys) ----------------------
  final prefs = await SharedPreferences.getInstance();
  const secureStorage = FlutterSecureStorage();
  final storage = StorageService(secureStorage, prefs);

  // --- Engine subprocess ----------------------------------------------------
  // Start the JAR immediately. EngineProcessService.start() resolves as soon
  // as it reads LOOM_PORT and LOOM_TOKEN from the process stdout (≈ 1–2 s).
  // The AppShell overlay covers the UI during this window so the user sees
  // "Starting the Loom engine…" rather than a blank/broken dashboard.
  final engine = EngineProcessService();
  engine.start(_resolveJarPath()).catchError((e) {
    // start() failed (JAR not found, Java not installed, port never announced).
    // Record the error so engineStateProvider can emit EngineState.unreachable
    // instead of remaining in starting indefinitely.
    debugPrint('[Loom] Engine start failed: $e');
    engine.recordStartFailure();
  });

  runApp(
    ProviderScope(
      overrides: [
        storageServiceProvider.overrideWithValue(storage),
        engineProcessServiceProvider.overrideWithValue(engine),
      ],
      child: const LoomApp(),
    ),
  );
}

class LoomApp extends ConsumerWidget {
  const LoomApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);

    return MaterialApp.router(
      title: 'Loom',
      debugShowCheckedModeBanner: false,
      theme: LoomTheme.light,
      darkTheme: LoomTheme.dark,
      themeMode: ThemeMode.light,
      routerConfig: router,
    );
  }
}
