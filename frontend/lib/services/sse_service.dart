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
      response.data!.stream
          .cast<List<int>>()
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .listen(
        (line) {
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
        },
        onDone: () {
          // Normal stream completion — reconnect if anyone is still listening.
          if (_controller != null && _controller!.hasListener) {
            Future.delayed(const Duration(seconds: 5), _connect);
          }
        },
        onError: (e) {
          // Do not reconnect on a cancellation or when there are no listeners.
          if (e is DioException && e.type == DioExceptionType.cancel) return;
          if (_controller == null || !_controller!.hasListener) return;
          Future.delayed(const Duration(seconds: 5), _connect);
        },
        cancelOnError: true,
      );
    }).catchError((e) {
      // Request itself failed (e.g. connection refused before streaming began).
      if (e is DioException && e.type == DioExceptionType.cancel) return;
      if (_controller == null || !_controller!.hasListener) return;
      Future.delayed(const Duration(seconds: 5), _connect);
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
