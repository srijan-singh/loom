import 'dart:async';
import 'dart:io';

/// Manages the Java engine subprocess lifecycle.
///
/// Launches the JAR, parses LOOM_PORT and LOOM_TOKEN from the process stdout,
/// and tees all stdout + stderr to a rolling log file so the engine is
/// debuggable even when Flutter owns the process.
///
/// Log file location: `$TMPDIR/loom-engine.log`
class EngineProcessService {
  static const _startupTimeout = Duration(seconds: 30);

  Process? _process;
  String? _port;
  String? _token;
  String? _logPath;

  String? get port => _port;
  String? get token => _token;

  /// Absolute path of the engine log file, available once [start] has been called.
  String? get logPath => _logPath;

  bool get isRunning => _process != null;

  /// Resolves [jarPath] to an absolute path.
  ///
  /// If [jarPath] is already absolute, it is returned as-is.
  /// If relative, it is resolved against the directory containing the running
  /// Dart executable — which for a built macOS app is inside the .app bundle,
  /// but for `flutter run` debug builds is the project's `macos/` directory.
  /// That makes `../../backend/loom/build/libs/loom-engine.jar` work from
  /// the repo without any `--dart-define`.
  static String resolveJarPath(String jarPath) {
    if (File(jarPath).isAbsolute) return jarPath;
    // Platform.resolvedExecutable is e.g.:
    //   debug:   .../frontend/build/macos/Build/Products/Debug/loom_ui.app/…/loom_ui
    //   release: /Applications/Loom.app/Contents/MacOS/loom_ui
    final execDir = File(Platform.resolvedExecutable).parent.path;
    return Uri.directory(execDir).resolve(jarPath).toFilePath();
  }

  /// Starts the engine from [jarPath] (absolute or relative — see [resolveJarPath]).
  ///
  /// Resolves once LOOM_PORT and LOOM_TOKEN have been read from stdout.
  /// All stdout and stderr lines are written to [logPath] in real time.
  Future<void> start(String jarPath) async {
    if (_process != null) return;

    final resolved = resolveJarPath(jarPath);

    final logFile = await _openLogFile();
    _logPath = logFile.path;

    if (!File(resolved).existsSync()) {
      _writeLog(logFile, '[EngineProcessService] JAR not found at: $resolved\n');
      throw FileSystemException('JAR not found', resolved);
    }

    _process = await Process.start('java', ['-jar', resolved]);

    final portCompleter = Completer<String>();
    final tokenCompleter = Completer<String>();

    // Tee stdout: parse protocol lines AND write everything to the log file.
    _process!.stdout
        .transform(const SystemEncoding().decoder)
        .listen((chunk) {
      _writeLog(logFile, chunk);
      for (final line in chunk.split('\n')) {
        final trimmed = line.trim();
        if (trimmed.startsWith('LOOM_PORT=') && !portCompleter.isCompleted) {
          portCompleter.complete(trimmed.substring('LOOM_PORT='.length));
        } else if (trimmed.startsWith('LOOM_TOKEN=') &&
            !tokenCompleter.isCompleted) {
          tokenCompleter.complete(trimmed.substring('LOOM_TOKEN='.length));
        }
      }
    });

    // Tee stderr to the same log file (Javalin/SLF4J logs go here).
    _process!.stderr
        .transform(const SystemEncoding().decoder)
        .listen((chunk) => _writeLog(logFile, chunk));

    _port = await portCompleter.future.timeout(
      _startupTimeout,
      onTimeout: () => throw TimeoutException(
          'Engine did not announce LOOM_PORT within ${_startupTimeout.inSeconds}s. '
          'Check $logPath for details.'),
    );
    _token = await tokenCompleter.future.timeout(
      _startupTimeout,
      onTimeout: () => throw TimeoutException(
          'Engine did not announce LOOM_TOKEN within ${_startupTimeout.inSeconds}s. '
          'Check $logPath for details.'),
    );
  }

  void dispose() {
    _process?.kill();
    _process = null;
    _port = null;
    _token = null;
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /// Opens (and truncates) the log file, writing a startup header.
  Future<File> _openLogFile() async {
    final tmpDir = _tmpDir();
    final file = File('$tmpDir/loom-engine.log');
    // Truncate on every launch so the file doesn't grow unboundedly.
    await file.writeAsString(
      '=== Loom engine started at ${DateTime.now().toIso8601String()} ===\n',
      mode: FileMode.write,
      flush: true,
    );
    return file;
  }

  void _writeLog(File file, String chunk) {
    // Fire-and-forget append — log loss on crash is acceptable.
    file
        .writeAsString(chunk, mode: FileMode.append, flush: false)
        .catchError((_) => file); // return file to satisfy Future<File> type
  }

  String _tmpDir() {
    // macOS sets TMPDIR to a per-user temp directory.
    final env = Platform.environment['TMPDIR'];
    if (env != null && env.isNotEmpty) return env.trimRight().replaceAll(RegExp(r'/$'), '');
    if (Platform.isMacOS || Platform.isLinux) return '/tmp';
    if (Platform.isWindows) {
      return Platform.environment['TEMP'] ??
          Platform.environment['TMP'] ??
          'C:\\Windows\\Temp';
    }
    return '/tmp';
  }
}
