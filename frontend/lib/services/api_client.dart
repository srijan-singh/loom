import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:loom_ui/models/agent_definition.dart';
import 'package:loom_ui/models/llm_connection.dart';
import 'package:loom_ui/models/mcp_connection.dart';
import 'package:loom_ui/models/skill.dart';
import 'package:loom_ui/services/engine_process_service.dart';

enum LlmTestFailure { rejected, unreachable }

class LlmTestException implements Exception {
  final LlmTestFailure failure;
  /// Backend-supplied error message when the test endpoint returns ok:false.
  final String? message;
  const LlmTestException(this.failure, {this.message});
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

  ApiClient(this._engine, {Dio? dio})
      : _dio = dio ??
            Dio(
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

  Future<dynamic> put(String path, {dynamic data}) =>
      _dio.put('$_baseUrl$path', data: data, options: _opts).then((r) => r.data);

  Future<dynamic> patch(String path, {dynamic data}) =>
      _dio.patch('$_baseUrl$path', data: data, options: _opts).then((r) => r.data);

  Future<dynamic> delete(String path) =>
      _dio.delete('$_baseUrl$path', options: _opts).then((r) => r.data);

  // ---------------------------------------------------------------------------
  // Domain helpers used by providers
  // ---------------------------------------------------------------------------

  Future<List<dynamic>> getSkills() =>
      get('/skills').then((d) => d as List<dynamic>);

  Future<Skill> getSkill(String id) =>
      get('/skills/$id').then((d) => Skill.fromJson(d as Map<String, dynamic>));

  Future<Skill> createSkill(Skill skill) =>
      post('/skills', data: skill.toJson())
          .then((d) => Skill.fromJson(d as Map<String, dynamic>));

  Future<Skill> updateSkill(String id, Skill skill) =>
      put('/skills/$id', data: skill.toJson())
          .then((d) => Skill.fromJson(d as Map<String, dynamic>));

  Future<void> deleteSkill(String id) =>
      delete('/skills/$id').then((_) {});

  Future<List<dynamic>> getMcps() =>
      get('/mcps').then((d) => d as List<dynamic>);

  Future<MCPConnection> createMcp(Map<String, dynamic> payload) =>
      post('/mcps', data: payload)
          .then((d) => MCPConnection.fromJson(d as Map<String, dynamic>));

  Future<void> updateMcpStatus(String id, String status) =>
      put('/mcps/$id', data: {'status': status}).then((_) {});

  Future<void> deleteMcp(String id) =>
      delete('/mcps/$id').then((_) {});

  Future<List<dynamic>> getAgents() =>
      get('/agents').then((d) => d as List<dynamic>);

  Future<AgentDefinition> getAgent(String id) =>
      get('/agents/$id')
          .then((d) => AgentDefinition.fromJson(d as Map<String, dynamic>));

  Future<AgentDefinition> createAgent(AgentDefinition agent) =>
      post('/agents', data: agent.toJson())
          .then((d) => AgentDefinition.fromJson(d as Map<String, dynamic>));

  Future<AgentDefinition> updateAgent(String id, AgentDefinition agent) =>
      put('/agents/$id', data: agent.toJson())
          .then((d) => AgentDefinition.fromJson(d as Map<String, dynamic>));

  Future<void> deleteAgent(String id) =>
      delete('/agents/$id').then((_) {});

  /// Returns [limit] most-recent sessions.
  Future<List<dynamic>> getRecentSessions({int limit = 5}) =>
      get('/sessions').then((d) => (d as List<dynamic>).take(limit).toList());

  // ---------------------------------------------------------------------------
  // LLM Connections
  // ---------------------------------------------------------------------------

  Future<List<LlmConnection>> getLlmConnections() =>
      get('/llm/connections').then((d) => (d as List<dynamic>)
          .map((e) => LlmConnection.fromJson(e as Map<String, dynamic>))
          .toList());

  Future<LlmConnection> createLlmConnection(Map<String, dynamic> payload) =>
      post('/llm/connections', data: payload)
          .then((d) => LlmConnection.fromJson(d as Map<String, dynamic>));

  Future<void> deleteLlmConnection(String id) =>
      delete('/llm/connections/$id').then((_) {});

  /// PATCH /llm/connections/{id}/default — marks the given connection as default.
  Future<LlmConnection> setDefaultLlmConnection(String id) =>
      patch('/llm/connections/$id/default')
          .then((d) => LlmConnection.fromJson(d as Map<String, dynamic>));

  /// POST /llm/connections/test — tests the given endpoint configuration.
  ///
  /// The backend always returns HTTP 200.  Returns normally on `{ok:true}`.
  /// Throws [LlmTestException] with [LlmTestFailure.rejected] and the backend
  /// error message on `{ok:false}`.
  /// Throws [LlmTestException] with [LlmTestFailure.unreachable] on transport
  /// errors (connection refused, timeout, etc.).
  Future<void> testLlmConnection({
    required String baseUrl,
    required String apiKey,
    required String model,
  }) async {
    try {
      final resp = await post('/llm/connections/test', data: {
        'baseUrl': baseUrl,
        'apiKey': apiKey,
        'model': model,
      }) as Map<String, dynamic>;

      if (resp['ok'] != true) {
        final errMsg = resp['error'] as String?;
        throw LlmTestException(LlmTestFailure.rejected, message: errMsg);
      }
    } on LlmTestException {
      rethrow;
    } on DioException catch (e) {
      // A 401 from the engine means the session token is stale (e.g. after a
      // hot restart while the engine kept running). Surface a clear message
      // so the user knows to fully restart the app rather than chase the URL.
      if (e.response?.statusCode == 401) {
        throw const LlmTestException(
          LlmTestFailure.unreachable,
          message:
              'Session token mismatch — fully restart the app (not hot reload) to resync with the engine.',
        );
      }
      throw const LlmTestException(
        LlmTestFailure.unreachable,
        message: "Can't reach the Loom engine. Make sure the backend is running.",
      );
    } catch (_) {
      throw const LlmTestException(LlmTestFailure.unreachable);
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

/// Polls the engine health endpoint and exposes [EngineState].
/// Checks immediately on first listen, then waits 5 s between each check.
final engineStateProvider =
    StreamProvider.autoDispose<EngineState>((ref) async* {
  final client = ref.watch(apiClientProvider);
  final engine = ref.watch(engineProcessServiceProvider);

  // If the engine process hasn't announced its port yet, we're still starting
  // (unless start() already failed, in which case we're unreachable).
  if (engine.port == null) {
    yield engine.startFailed ? EngineState.unreachable : EngineState.starting;
  }

  // Check immediately, then every 5 s.
  while (true) {
    try {
      await client.get('/health');
      yield EngineState.running;
    } catch (_) {
      yield engine.startFailed || engine.port != null
          ? EngineState.unreachable
          : EngineState.starting;
    }
    await Future.delayed(const Duration(seconds: 5));
  }
});
