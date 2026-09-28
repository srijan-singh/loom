import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:loom_ui/services/engine_process_service.dart';

enum LlmTestFailure { rejected, unreachable }

class LlmTestException implements Exception {
  final LlmTestFailure failure;
  const LlmTestException(this.failure);
}

enum EngineState { starting, running, unreachable }

/// Thin Dio wrapper that injects the per-launch token on every request.
///
/// Base URL is resolved from [EngineProcessService] at call time, allowing the
/// app to be constructed before the engine subprocess is fully up.
class ApiClient {
  /// Default when running the engine manually during development.
  static const _devUrl = String.fromEnvironment(
    'LOOM_ENGINE_URL',
    defaultValue: 'http://127.0.0.1:7070',
  );

  /// Injected at build time via `--dart-define=LOOM_TOKEN=<value>`.
  /// Used when Flutter is not managing the engine subprocess itself.
  static const _devToken = String.fromEnvironment('LOOM_TOKEN');

  final Dio _dio;
  final EngineProcessService _engine;

  ApiClient(this._engine)
      : _dio = Dio(
          BaseOptions(
            connectTimeout: const Duration(seconds: 3),
            receiveTimeout: const Duration(seconds: 30),
          ),
        );

  String get _baseUrl {
    final port = _engine.port;
    return port != null ? 'http://127.0.0.1:$port' : _devUrl;
  }

  String? get _token {
    final fromProcess = _engine.token;
    if (fromProcess != null) return fromProcess;
    if (_devToken.isNotEmpty) return _devToken;
    return null;
  }

  Options get _opts => Options(
        headers: {
          if (_token != null) 'X-Loom-Token': _token,
        },
      );

  // ---------------------------------------------------------------------------
  // Generic helpers
  // ---------------------------------------------------------------------------

  Future<dynamic> get(String path, {Map<String, dynamic>? queryParameters}) =>
      _dio
          .get('$_baseUrl$path',
              queryParameters: queryParameters, options: _opts)
          .then((r) => r.data);

  Future<dynamic> post(String path, {dynamic data}) =>
      _dio.post('$_baseUrl$path', data: data, options: _opts).then((r) => r.data);

  // ---------------------------------------------------------------------------
  // Domain helpers used by providers
  // ---------------------------------------------------------------------------

  Future<List<dynamic>> getSkills() =>
      get('/skills').then((d) => d as List<dynamic>);

  Future<List<dynamic>> getMcps() =>
      get('/mcps').then((d) => d as List<dynamic>);

  Future<List<dynamic>> getAgents() =>
      get('/agents').then((d) => d as List<dynamic>);

  /// Returns [limit] most-recent sessions.
  Future<List<dynamic>> getRecentSessions({int limit = 5}) =>
      get('/sessions').then((d) => (d as List<dynamic>).take(limit).toList());

  /// POST /llm/test — returns true on 2xx, false on 4xx (rejected key),
  /// throws [LlmTestException] with a [LlmTestFailure] discriminant on error.
  Future<void> testLlm(String provider, String apiKey, String model) async {
    try {
      final resp = await _dio.post(
        '$_baseUrl/llm/test',
        data: {'provider': provider, 'apiKey': apiKey, 'model': model},
        options: Options(
          headers: {if (_token != null) 'X-Loom-Token': _token},
          validateStatus: (s) => s != null && s < 500,
        ),
      );
      if (resp.statusCode != null && resp.statusCode! >= 400) {
        throw const LlmTestException(LlmTestFailure.rejected);
      }
    } on DioException {
      throw const LlmTestException(LlmTestFailure.unreachable);
    }
  }

  /// GET /llm/models?provider=[provider] — returns model IDs.
  /// Falls back to an empty list so callers can use hardcoded defaults.
  Future<List<String>> getModels(String provider) async {
    try {
      final data = await get('/llm/models', queryParameters: {'provider': provider});
      return (data as List<dynamic>).map((e) => e.toString()).toList();
    } catch (_) {
      return [];
    }
  }
}

// ---------------------------------------------------------------------------
// Riverpod providers
// ---------------------------------------------------------------------------

final engineProcessServiceProvider = Provider<EngineProcessService>(
  (ref) => EngineProcessService(),
);

final apiClientProvider = Provider<ApiClient>(
  (ref) => ApiClient(ref.watch(engineProcessServiceProvider)),
);

/// Polls the engine health endpoint every 5 s and exposes [EngineState].
/// Starts in [EngineState.starting] until the first successful ping.
final engineStateProvider =
    StreamProvider.autoDispose<EngineState>((ref) async* {
  final client = ref.watch(apiClientProvider);
  final engine = ref.watch(engineProcessServiceProvider);

  // If the engine process hasn't announced its port yet, we're starting.
  if (engine.port == null) {
    yield EngineState.starting;
  }

  await for (final _ in Stream.periodic(const Duration(seconds: 5),
      (i) => i)..take(9999)) {
    try {
      await client.get('/health');
      yield EngineState.running;
    } catch (_) {
      yield engine.port == null
          ? EngineState.starting
          : EngineState.unreachable;
    }
  }
});
