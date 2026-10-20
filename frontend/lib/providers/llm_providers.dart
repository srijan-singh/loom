import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:loom_ui/models/llm_connection.dart';
import 'package:loom_ui/services/api_client.dart';

class LlmConnectionsNotifier extends AsyncNotifier<List<LlmConnection>> {
  @override
  Future<List<LlmConnection>> build() async {
    final client = ref.watch(apiClientProvider);
    return client.getLlmConnections();
  }

  Future<LlmConnection> create({
    required String name,
    required String baseUrl,
    required String model,
    required String apiKey,
    bool isDefault = false,
  }) async {
    final client = ref.read(apiClientProvider);
    final created = await client.createLlmConnection({
      'name': name,
      'baseUrl': baseUrl,
      'model': model,
      'apiKey': apiKey,
      'isDefault': isDefault,
    });
    ref.invalidateSelf();
    return created;
  }

  Future<void> delete(String id) async {
    final client = ref.read(apiClientProvider);
    await client.deleteLlmConnection(id);
    ref.invalidateSelf();
  }

  Future<void> setDefault(String id) async {
    final client = ref.read(apiClientProvider);
    await client.setDefaultLlmConnection(id);
    ref.invalidateSelf();
  }
}

final llmConnectionsProvider =
    AsyncNotifierProvider<LlmConnectionsNotifier, List<LlmConnection>>(
        LlmConnectionsNotifier.new);
