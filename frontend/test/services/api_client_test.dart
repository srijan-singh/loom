import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loom_ui/models/agent_definition.dart';
import 'package:loom_ui/models/mcp_connection.dart';
import 'package:loom_ui/models/skill.dart';
import 'package:loom_ui/providers/library_providers.dart';
import 'package:loom_ui/services/api_client.dart';
import 'package:loom_ui/services/engine_process_service.dart';

class MockHttpClientAdapter implements HttpClientAdapter {
  Future<ResponseBody> Function(RequestOptions options)? handler;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (handler != null) {
      return handler!(options);
    }
    throw UnimplementedError('No handler set for MockHttpClientAdapter');
  }

  @override
  void close({bool force = false}) {}
}

ResponseBody jsonResponse(dynamic data, {int statusCode = 200}) {
  return ResponseBody.fromString(
    jsonEncode(data),
    statusCode,
    headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    },
  );
}

class FakeApiClient extends ApiClient {
  FakeApiClient() : super(EngineProcessService());

  List<dynamic> skillsData = [];
  List<dynamic> mcpsData = [];
  List<dynamic> agentsData = [];

  bool shouldThrow = false;
  Completer<List<dynamic>>? skillsCompleter;
  Completer<List<dynamic>>? mcpsCompleter;
  Completer<List<dynamic>>? agentsCompleter;

  Skill? createdSkill;
  Skill? updatedSkill;
  String? deletedSkillId;

  MCPConnection? createdMcp;
  String? updatedMcpStatusId;
  String? updatedMcpStatusValue;
  String? deletedMcpId;

  AgentDefinition? createdAgent;
  AgentDefinition? updatedAgent;
  String? deletedAgentId;

  @override
  Future<List<dynamic>> getSkills() {
    if (shouldThrow) throw Exception('Skills fetch error');
    if (skillsCompleter != null) return skillsCompleter!.future;
    return Future.value(skillsData);
  }

  @override
  Future<Skill> createSkill(Skill skill) async {
    if (shouldThrow) throw Exception('Create skill error');
    createdSkill = skill;
    return skill;
  }

  @override
  Future<Skill> updateSkill(String id, Skill skill) async {
    if (shouldThrow) throw Exception('Update skill error');
    updatedSkill = skill;
    return skill;
  }

  @override
  Future<void> deleteSkill(String id) async {
    if (shouldThrow) throw Exception('Delete skill error');
    deletedSkillId = id;
  }

  @override
  Future<List<dynamic>> getMcps() {
    if (shouldThrow) throw Exception('MCPs fetch error');
    if (mcpsCompleter != null) return mcpsCompleter!.future;
    return Future.value(mcpsData);
  }

  @override
  Future<MCPConnection> createMcp(Map<String, dynamic> payload) async {
    if (shouldThrow) throw Exception('Create MCP error');
    createdMcp = MCPConnection.fromJson(payload);
    return createdMcp!;
  }

  @override
  Future<void> updateMcpStatus(String id, String status) async {
    if (shouldThrow) throw Exception('Update MCP status error');
    updatedMcpStatusId = id;
    updatedMcpStatusValue = status;
  }

  @override
  Future<void> deleteMcp(String id) async {
    if (shouldThrow) throw Exception('Delete MCP error');
    deletedMcpId = id;
  }

  @override
  Future<List<dynamic>> getAgents() {
    if (shouldThrow) throw Exception('Agents fetch error');
    if (agentsCompleter != null) return agentsCompleter!.future;
    return Future.value(agentsData);
  }

  @override
  Future<AgentDefinition> createAgent(AgentDefinition agent) async {
    if (shouldThrow) throw Exception('Create agent error');
    createdAgent = agent;
    return agent;
  }

  @override
  Future<AgentDefinition> updateAgent(String id, AgentDefinition agent) async {
    if (shouldThrow) throw Exception('Update agent error');
    updatedAgent = agent;
    return agent;
  }

  @override
  Future<void> deleteAgent(String id) async {
    if (shouldThrow) throw Exception('Delete agent error');
    deletedAgentId = id;
  }
}

void main() {
  group('ApiClient CRUD Endpoints', () {
    late Dio dio;
    late MockHttpClientAdapter mockAdapter;
    late EngineProcessService engine;
    late ApiClient client;

    setUp(() {
      dio = Dio();
      mockAdapter = MockHttpClientAdapter();
      dio.httpClientAdapter = mockAdapter;
      engine = EngineProcessService();
      client = ApiClient(engine, dio: dio);
    });

    test('getSkill sends GET to /skills/{id} and returns Skill', () async {
      mockAdapter.handler = (options) async {
        expect(options.method, 'GET');
        expect(options.path, 'http://127.0.0.1:7070/skills/skill-1');
        return jsonResponse({
          'id': 'skill-1',
          'name': 'Test Skill',
          'description': 'A skill description',
          'content': 'Skill content',
          'tags': ['dart', 'flutter'],
          'createdAt': 1000,
          'updatedAt': 2000,
        });
      };

      final result = await client.getSkill('skill-1');
      expect(result.id, 'skill-1');
      expect(result.name, 'Test Skill');
      expect(result.tags, ['dart', 'flutter']);
      expect(result.createdAt, 1000);
      expect(result.updatedAt, 2000);
    });

    test('createSkill sends POST to /skills with skill JSON payload and returns created Skill', () async {
      const skill = Skill(
        id: 'new-skill',
        name: 'New Skill',
        description: 'New Description',
        content: 'New Content',
        tags: ['test'],
        createdAt: 100,
        updatedAt: 100,
      );

      mockAdapter.handler = (options) async {
        expect(options.method, 'POST');
        expect(options.path, 'http://127.0.0.1:7070/skills');
        final data = options.data as Map<String, dynamic>;
        expect(data['name'], 'New Skill');
        expect(data['description'], 'New Description');
        expect(data['tags'], ['test']);
        return jsonResponse(skill.toJson(), statusCode: 201);
      };

      final created = await client.createSkill(skill);
      expect(created.id, 'new-skill');
      expect(created.name, 'New Skill');
    });

    test('updateSkill sends PUT to /skills/{id} with updated fields', () async {
      const skill = Skill(
        id: 'skill-1',
        name: 'Updated Skill',
        description: 'Updated Description',
        content: 'Updated Content',
        tags: ['updated'],
        createdAt: 100,
        updatedAt: 200,
      );

      mockAdapter.handler = (options) async {
        expect(options.method, 'PUT');
        expect(options.path, 'http://127.0.0.1:7070/skills/skill-1');
        final data = options.data as Map<String, dynamic>;
        expect(data['name'], 'Updated Skill');
        return jsonResponse(skill.toJson());
      };

      final updated = await client.updateSkill('skill-1', skill);
      expect(updated.id, 'skill-1');
      expect(updated.name, 'Updated Skill');
      expect(updated.updatedAt, 200);
    });

    test('deleteSkill sends DELETE to /skills/{id}', () async {
      mockAdapter.handler = (options) async {
        expect(options.method, 'DELETE');
        expect(options.path, 'http://127.0.0.1:7070/skills/skill-1');
        return jsonResponse(null, statusCode: 204);
      };

      await expectLater(client.deleteSkill('skill-1'), completes);
    });

    test('createMcp sends POST to /mcps and returns MCPConnection', () async {
      final payload = {
        'id': 'mcp-1',
        'name': 'GitHub MCP',
        'type': 'GITHUB',
        'status': 'CONNECTED',
        'tools': ['list_repos'],
        'detail': 'Connected to GitHub',
        'createdAt': 500,
      };

      mockAdapter.handler = (options) async {
        expect(options.method, 'POST');
        expect(options.path, 'http://127.0.0.1:7070/mcps');
        expect(options.data, payload);
        return jsonResponse(payload, statusCode: 201);
      };

      final result = await client.createMcp(payload);
      expect(result.id, 'mcp-1');
      expect(result.name, 'GitHub MCP');
      expect(result.type, 'GITHUB');
      expect(result.status, MCPStatus.CONNECTED);
    });

    test('updateMcpStatus sends PUT to /mcps/{id}', () async {
      mockAdapter.handler = (options) async {
        expect(options.method, 'PUT');
        expect(options.path, 'http://127.0.0.1:7070/mcps/mcp-1');
        final data = options.data as Map<String, dynamic>;
        expect(data['status'], 'DISCONNECTED');
        return jsonResponse(null, statusCode: 200);
      };

      await expectLater(client.updateMcpStatus('mcp-1', 'DISCONNECTED'), completes);
    });

    test('deleteMcp sends DELETE to /mcps/{id}', () async {
      mockAdapter.handler = (options) async {
        expect(options.method, 'DELETE');
        expect(options.path, 'http://127.0.0.1:7070/mcps/mcp-1');
        return jsonResponse(null, statusCode: 204);
      };

      await expectLater(client.deleteMcp('mcp-1'), completes);
    });

    test('createAgent, updateAgent, and deleteAgent interact with /agents routes correctly', () async {
      const agent = AgentDefinition(
        id: 'agent-1',
        name: 'Research Agent',
        roleDescription: 'Researches topics',
        skillId: 'skill-1',
        allowedMcpIds: ['mcp-1'],
        createdAt: 100,
        updatedAt: 200,
      );

      // GET /agents/{id}
      mockAdapter.handler = (options) async {
        expect(options.method, 'GET');
        expect(options.path, 'http://127.0.0.1:7070/agents/agent-1');
        return jsonResponse(agent.toJson());
      };
      final fetched = await client.getAgent('agent-1');
      expect(fetched.id, 'agent-1');
      expect(fetched.name, 'Research Agent');

      // POST /agents
      mockAdapter.handler = (options) async {
        expect(options.method, 'POST');
        expect(options.path, 'http://127.0.0.1:7070/agents');
        final data = options.data as Map<String, dynamic>;
        expect(data['name'], 'Research Agent');
        return jsonResponse(agent.toJson(), statusCode: 201);
      };
      final created = await client.createAgent(agent);
      expect(created.id, 'agent-1');

      // PUT /agents/{id}
      final updatedAgent = agent.copyWith(name: 'Updated Agent');
      mockAdapter.handler = (options) async {
        expect(options.method, 'PUT');
        expect(options.path, 'http://127.0.0.1:7070/agents/agent-1');
        final data = options.data as Map<String, dynamic>;
        expect(data['name'], 'Updated Agent');
        return jsonResponse(updatedAgent.toJson());
      };
      final updated = await client.updateAgent('agent-1', updatedAgent);
      expect(updated.name, 'Updated Agent');

      // DELETE /agents/{id}
      mockAdapter.handler = (options) async {
        expect(options.method, 'DELETE');
        expect(options.path, 'http://127.0.0.1:7070/agents/agent-1');
        return jsonResponse(null, statusCode: 204);
      };
      await expectLater(client.deleteAgent('agent-1'), completes);
    });
  });

  group('Riverpod Library Providers State Transitions', () {
    group('skillsProvider', () {
      test('transitions from Loading -> Data and handles mutations', () async {
        final fakeClient = FakeApiClient();
        fakeClient.skillsCompleter = Completer<List<dynamic>>();

        final container = ProviderContainer(
          overrides: [
            apiClientProvider.overrideWithValue(fakeClient),
          ],
        );
        addTearDown(container.dispose);

        // Initial read triggers build and is Loading
        expect(container.read(skillsProvider), isA<AsyncLoading<List<Skill>>>());

        // Complete the fetch
        fakeClient.skillsCompleter!.complete([
          {
            'id': 'skill-1',
            'name': 'Skill One',
            'description': 'Desc',
            'content': 'Content',
            'tags': ['a'],
            'createdAt': 100,
            'updatedAt': 100,
          }
        ]);

        final data = await container.read(skillsProvider.future);
        expect(data.length, 1);
        expect(data.first.id, 'skill-1');
        expect(container.read(skillsProvider).value, data);

        // Test createSkill mutation
        const newSkill = Skill(
          id: 'skill-2',
          name: 'Skill Two',
          createdAt: 200,
          updatedAt: 200,
        );
        fakeClient.skillsCompleter = null;
        fakeClient.skillsData = [
          {
            'id': 'skill-1',
            'name': 'Skill One',
            'createdAt': 100,
            'updatedAt': 100,
          },
          newSkill.toJson(),
        ];

        final created = await container.read(skillsProvider.notifier).createSkill(newSkill);
        expect(created.id, 'skill-2');
        expect(fakeClient.createdSkill?.id, 'skill-2');

        final refreshedData = await container.read(skillsProvider.future);
        expect(refreshedData.length, 2);

        // Test updateSkill mutation
        final updatedSkill = newSkill.copyWith(name: 'Skill Two Updated');
        fakeClient.skillsData = [
          {
            'id': 'skill-1',
            'name': 'Skill One',
            'createdAt': 100,
            'updatedAt': 100,
          },
          updatedSkill.toJson(),
        ];
        final updated = await container.read(skillsProvider.notifier).updateSkill('skill-2', updatedSkill);
        expect(updated.name, 'Skill Two Updated');
        expect(fakeClient.updatedSkill?.name, 'Skill Two Updated');

        // Test deleteSkill mutation
        fakeClient.skillsData = [
          {
            'id': 'skill-1',
            'name': 'Skill One',
            'createdAt': 100,
            'updatedAt': 100,
          },
        ];
        await container.read(skillsProvider.notifier).deleteSkill('skill-2');
        expect(fakeClient.deletedSkillId, 'skill-2');

        final afterDelete = await container.read(skillsProvider.future);
        expect(afterDelete.length, 1);
      });

      test('transitions from Loading -> Error when ApiClient throws', () async {
        final fakeClient = FakeApiClient();
        fakeClient.shouldThrow = true;

        final container = ProviderContainer(
          overrides: [
            apiClientProvider.overrideWithValue(fakeClient),
          ],
        );
        addTearDown(container.dispose);

        // Expect future to throw and state to be AsyncError
        await expectLater(container.read(skillsProvider.future), throwsA(isA<Exception>()));
        expect(container.read(skillsProvider), isA<AsyncError<List<Skill>>>());
      });
    });

    group('mcpsProvider', () {
      test('transitions from Loading -> Data and handles mutations', () async {
        final fakeClient = FakeApiClient();
        fakeClient.mcpsCompleter = Completer<List<dynamic>>();

        final container = ProviderContainer(
          overrides: [
            apiClientProvider.overrideWithValue(fakeClient),
          ],
        );
        addTearDown(container.dispose);

        expect(container.read(mcpsProvider), isA<AsyncLoading<List<MCPConnection>>>());

        fakeClient.mcpsCompleter!.complete([
          {
            'id': 'mcp-1',
            'name': 'GitHub',
            'type': 'GITHUB',
            'status': 'CONNECTED',
            'tools': ['t1'],
            'createdAt': 100,
          }
        ]);

        final data = await container.read(mcpsProvider.future);
        expect(data.length, 1);
        expect(data.first.id, 'mcp-1');
        expect(container.read(mcpsProvider).value, data);

        // Test createMcp
        fakeClient.mcpsCompleter = null;
        fakeClient.mcpsData = [
          {
            'id': 'mcp-1',
            'name': 'GitHub',
            'type': 'GITHUB',
            'status': 'CONNECTED',
            'tools': ['t1'],
            'createdAt': 100,
          },
          {
            'id': 'mcp-2',
            'name': 'Figma',
            'type': 'FIGMA',
            'status': 'DISCONNECTED',
            'createdAt': 200,
          }
        ];

        final created = await container.read(mcpsProvider.notifier).createMcp({
          'id': 'mcp-2',
          'name': 'Figma',
          'type': 'FIGMA',
          'createdAt': 200,
        });
        expect(created.id, 'mcp-2');
        expect(fakeClient.createdMcp?.id, 'mcp-2');

        final refreshed = await container.read(mcpsProvider.future);
        expect(refreshed.length, 2);

        // Test updateMcpStatus
        await container.read(mcpsProvider.notifier).updateMcpStatus('mcp-2', 'CONNECTED');
        expect(fakeClient.updatedMcpStatusId, 'mcp-2');
        expect(fakeClient.updatedMcpStatusValue, 'CONNECTED');

        // Test deleteMcp
        fakeClient.mcpsData = [
          {
            'id': 'mcp-1',
            'name': 'GitHub',
            'type': 'GITHUB',
            'status': 'CONNECTED',
            'tools': ['t1'],
            'createdAt': 100,
          },
        ];
        await container.read(mcpsProvider.notifier).deleteMcp('mcp-2');
        expect(fakeClient.deletedMcpId, 'mcp-2');

        final afterDelete = await container.read(mcpsProvider.future);
        expect(afterDelete.length, 1);
      });

      test('transitions from Loading -> Error when ApiClient throws', () async {
        final fakeClient = FakeApiClient();
        fakeClient.shouldThrow = true;

        final container = ProviderContainer(
          overrides: [
            apiClientProvider.overrideWithValue(fakeClient),
          ],
        );
        addTearDown(container.dispose);

        await expectLater(container.read(mcpsProvider.future), throwsA(isA<Exception>()));
        expect(container.read(mcpsProvider), isA<AsyncError<List<MCPConnection>>>());
      });
    });

    group('agentsProvider', () {
      test('transitions from Loading -> Data and handles mutations', () async {
        final fakeClient = FakeApiClient();
        fakeClient.agentsCompleter = Completer<List<dynamic>>();

        final container = ProviderContainer(
          overrides: [
            apiClientProvider.overrideWithValue(fakeClient),
          ],
        );
        addTearDown(container.dispose);

        expect(container.read(agentsProvider), isA<AsyncLoading<List<AgentDefinition>>>());

        fakeClient.agentsCompleter!.complete([
          {
            'id': 'agent-1',
            'name': 'Agent One',
            'roleDescription': 'Role',
            'skillId': 'skill-1',
            'allowedMcpIds': ['mcp-1'],
            'createdAt': 100,
            'updatedAt': 100,
          }
        ]);

        final data = await container.read(agentsProvider.future);
        expect(data.length, 1);
        expect(data.first.id, 'agent-1');
        expect(container.read(agentsProvider).value, data);

        // Test createAgent
        const newAgent = AgentDefinition(
          id: 'agent-2',
          name: 'Agent Two',
          createdAt: 200,
          updatedAt: 200,
        );
        fakeClient.agentsCompleter = null;
        fakeClient.agentsData = [
          {
            'id': 'agent-1',
            'name': 'Agent One',
            'roleDescription': 'Role',
            'skillId': 'skill-1',
            'allowedMcpIds': ['mcp-1'],
            'createdAt': 100,
            'updatedAt': 100,
          },
          newAgent.toJson(),
        ];

        final created = await container.read(agentsProvider.notifier).createAgent(newAgent);
        expect(created.id, 'agent-2');
        expect(fakeClient.createdAgent?.id, 'agent-2');

        final refreshed = await container.read(agentsProvider.future);
        expect(refreshed.length, 2);

        // Test updateAgent
        final updatedAgent = newAgent.copyWith(name: 'Agent Two Updated');
        fakeClient.agentsData = [
          {
            'id': 'agent-1',
            'name': 'Agent One',
            'roleDescription': 'Role',
            'skillId': 'skill-1',
            'allowedMcpIds': ['mcp-1'],
            'createdAt': 100,
            'updatedAt': 100,
          },
          updatedAgent.toJson(),
        ];
        final updated = await container.read(agentsProvider.notifier).updateAgent('agent-2', updatedAgent);
        expect(updated.name, 'Agent Two Updated');
        expect(fakeClient.updatedAgent?.name, 'Agent Two Updated');

        // Test deleteAgent
        fakeClient.agentsData = [
          {
            'id': 'agent-1',
            'name': 'Agent One',
            'roleDescription': 'Role',
            'skillId': 'skill-1',
            'allowedMcpIds': ['mcp-1'],
            'createdAt': 100,
            'updatedAt': 100,
          },
        ];
        await container.read(agentsProvider.notifier).deleteAgent('agent-2');
        expect(fakeClient.deletedAgentId, 'agent-2');

        final afterDelete = await container.read(agentsProvider.future);
        expect(afterDelete.length, 1);
      });

      test('transitions from Loading -> Error when ApiClient throws', () async {
        final fakeClient = FakeApiClient();
        fakeClient.shouldThrow = true;

        final container = ProviderContainer(
          overrides: [
            apiClientProvider.overrideWithValue(fakeClient),
          ],
        );
        addTearDown(container.dispose);

        await expectLater(container.read(agentsProvider.future), throwsA(isA<Exception>()));
        expect(container.read(agentsProvider), isA<AsyncError<List<AgentDefinition>>>());
      });
    });
  });
}
