import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:loom_ui/models/workflow_event.dart';
import 'package:loom_ui/services/engine_process_service.dart';

/// Connects to the engine's SSE endpoint (`GET /events`) and exposes a
/// broadcast [Stream<WorkflowEvent>].
///
/// Token is passed as `?token=<token>` because standard [EventSource] /
/// chunked-stream clients cannot set custom headers.
class SseService {
  static const _devUrl = String.fromEnvironment(
    'LOOM_ENGINE_URL',
    defaultValue: 'http://127.0.0.1:7070',
  );

  final EngineProcessService _engine;
  final Dio _dio = Dio();

  StreamController<WorkflowEvent>? _controller;
  CancelToken? _cancel;

  SseService(this._engine);

  String get _baseUrl {
    final port = _engine.port;
    return port != null ? 'http://127.0.0.1:$port' : _devUrl;
  }

  /// A broadcast stream of [WorkflowEvent]s received from the engine.
  Stream<WorkflowEvent> get events {
    _controller ??= StreamController<WorkflowEvent>.broadcast(
      onListen: _connect,
      onCancel: _disconnect,
    );
    return _controller!.stream;
  }

  void _connect() {
    _cancel = CancelToken();
    final token = _engine.token ?? '';
    _dio
        .get<ResponseBody>(
      '$_baseUrl/events',
      queryParameters: {'token': token},
      options: Options(responseType: ResponseType.stream),
      cancelToken: _cancel,
    )
        .then((response) {
      response.data!.stream.listen(
        (chunk) {
          final decoded = utf8.decode(chunk);
          for (final line in const LineSplitter().convert(decoded)) {
            if (line.startsWith('data:')) {
              final payload = line.substring(5).trim();
              if (payload.isNotEmpty) {
                try {
                  final json = jsonDecode(payload) as Map<String, dynamic>;
                  _controller?.add(WorkflowEvent.fromJson(json));
                } catch (_) {
                  // Malformed SSE payload — skip silently.
                }
              }
            }
          }
        },
        onError: (_) => Future.delayed(
          const Duration(seconds: 5),
          () => _connect(),
        ),
      );
    }).catchError((_) {
      Future.delayed(const Duration(seconds: 5), () => _connect());
    });
  }

  void _disconnect() {
    _cancel?.cancel();
    _cancel = null;
  }

  void dispose() {
    _disconnect();
    _controller?.close();
    _controller = null;
  }
}
