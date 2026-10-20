import 'package:flutter_test/flutter_test.dart';
import 'package:loom_ui/models/agent_definition.dart';
import 'package:loom_ui/models/llm_connection.dart';
import 'package:loom_ui/models/mcp_connection.dart';
import 'package:loom_ui/models/session.dart';
import 'package:loom_ui/models/skill.dart';
import 'package:loom_ui/models/workflow_event.dart';
import 'package:loom_ui/models/workspace.dart';

void main() {
  group('Model serialization and deserialization', () {
    test('AgentDefinition.fromJson parses correctly', () {
      final json = {
        'id': 'agent-1',
        'name': 'Code Reviewer',
        'roleDescription': 'Reviews pull requests',
        'skillId': 'skill-1',
        'allowedMcpIds': ['mcp-git', 'mcp-fs'],
        'createdAt': 1700000000000,
        'updatedAt': 1700000050000,
      };

      final agent = AgentDefinition.fromJson(json);

      expect(agent.id, 'agent-1');
      expect(agent.name, 'Code Reviewer');
      expect(agent.roleDescription, 'Reviews pull requests');
      expect(agent.skillId, 'skill-1');
      expect(agent.allowedMcpIds, ['mcp-git', 'mcp-fs']);
      expect(agent.createdAt, 1700000000000);
      expect(agent.updatedAt, 1700000050000);
    });

    test('AgentDefinition.fromJson handles nulls and empty list', () {
      final json = {
        'id': 'agent-2',
        'name': 'Tester',
        'roleDescription': null,
        'skillId': null,
        'allowedMcpIds': null,
        'createdAt': 1700000000000,
        'updatedAt': 1700000050000,
      };

      final agent = AgentDefinition.fromJson(json);
      expect(agent.id, 'agent-2');
      expect(agent.roleDescription, '');
      expect(agent.skillId, isNull);
      expect(agent.allowedMcpIds, isEmpty);
    });

    test('Session.fromJson handles different status values', () {
      final jsonCompleted = {
        'id': 'session-1',
        'workspaceId': 'ws-1',
        'workflowDefinitionId': 'wf-1',
        'status': 'COMPLETED',
        'startedAt': 1700000000000,
        'completedAt': 1700000060000,
        'agentCount': 3,
      };
      final sessionCompleted = Session.fromJson(jsonCompleted);
      expect(sessionCompleted.status, SessionStatus.completed);
      expect(sessionCompleted.agentCount, 3);
      expect(sessionCompleted.completedAt, 1700000060000);

      final jsonRunning = {
        'id': 'session-2',
        'workspaceId': 'ws-1',
        'workflowDefinitionId': 'wf-1',
        'status': 'RUNNING',
        'startedAt': 1700000000000,
      };
      expect(Session.fromJson(jsonRunning).status, SessionStatus.running);

      final jsonFailed = {
        'id': 'session-3',
        'workspaceId': 'ws-1',
        'workflowDefinitionId': 'wf-1',
        'status': 'FAILED',
        'startedAt': 1700000000000,
      };
      expect(Session.fromJson(jsonFailed).status, SessionStatus.failed);

      final jsonPartial = {
        'id': 'session-4',
        'workspaceId': 'ws-1',
        'workflowDefinitionId': 'wf-1',
        'status': 'PARTIAL',
        'startedAt': 1700000000000,
      };
      expect(Session.fromJson(jsonPartial).status, SessionStatus.partial);
    });

    test('Workspace.fromJson parses correctly', () {
      final json = {
        'id': 'ws-1',
        'name': 'Main Workspace',
        'description': 'Dev workspace',
        'createdAt': 1700000000000,
        'updatedAt': 1700000050000,
      };
      final ws = Workspace.fromJson(json);
      expect(ws.id, 'ws-1');
      expect(ws.name, 'Main Workspace');
      expect(ws.description, 'Dev workspace');
    });

    test('MCPConnection.fromJson parses correctly', () {
      final json = {
        'id': 'mcp-1',
        'name': 'Filesystem MCP',
        'type': 'stdio',
        'status': 'CONNECTED',
        'createdAt': 1700000000000,
      };
      final mcp = MCPConnection.fromJson(json);
      expect(mcp.id, 'mcp-1');
      expect(mcp.name, 'Filesystem MCP');
      expect(mcp.type, 'stdio');
      expect(mcp.status, MCPStatus.CONNECTED);
    });

    test('Skill.fromJson parses correctly', () {
      final json = {
        'id': 'skill-1',
        'name': 'Code Review',
        'description': 'Performs automated code reviews',
        'content': 'You are a code reviewer.',
        'tags': ['review', 'code'],
        'createdAt': 1700000000000,
        'updatedAt': 1700000050000,
      };
      final skill = Skill.fromJson(json);
      expect(skill.id, 'skill-1');
      expect(skill.name, 'Code Review');
      expect(skill.description, 'Performs automated code reviews');
      expect(skill.content, 'You are a code reviewer.');
      expect(skill.tags, ['review', 'code']);
    });

    test('WorkflowEvent.fromJson parses correctly', () {
      final json = {
        'eventType': 'AGENT_TOKEN',
        'sessionId': 'session-1',
        'nodeId': 'node-1',
        'agentExecutionId': 'exec-1',
        'timestamp': 1700000000000,
        'data': {'token': 'Hello'},
      };
      final event = WorkflowEvent.fromJson(json);
      expect(event.sessionId, 'session-1');
      expect(event.nodeId, 'node-1');
      expect(event.agentExecutionId, 'exec-1');
      expect(event.type, WorkflowEventType.agentToken);
      expect(event.data?['token'], 'Hello');
    });
  });

  group('Model defaults, toJson and copyWith', () {
    test('Skill.fromJson handles empty/default values', () {
      final skill = Skill.fromJson({
        'id': 's',
        'name': 'n',
        'createdAt': 1,
        'updatedAt': 2,
      });
      expect(skill.description, '');
      expect(skill.content, '');
      expect(skill.tags, isEmpty);
    });

    test('Skill.toJson matches backend schema and copyWith keeps fields', () {
      const skill = Skill(
        id: 's1',
        name: 'Review',
        description: 'd',
        content: 'c',
        tags: ['a', 'b'],
        createdAt: 1,
        updatedAt: 2,
      );
      expect(skill.toJson(), {
        'id': 's1',
        'name': 'Review',
        'description': 'd',
        'content': 'c',
        'tags': ['a', 'b'],
        'createdAt': 1,
        'updatedAt': 2,
      });
      final copy = skill.copyWith(name: 'New', tags: ['x']);
      expect(copy.name, 'New');
      expect(copy.tags, ['x']);
      expect(copy.id, 's1');
      expect(copy.description, 'd');
      expect(copy.updatedAt, 2);
    });

    test(
      'MCPConnection.fromJson handles status strings, tools and nullables',
      () {
        final connected = MCPConnection.fromJson({
          'id': 'm1',
          'name': 'FS',
          'type': 'stdio',
          'status': 'CONNECTED',
          'tools': ['read', 'write'],
          'detail': 'ok',
          'config': {'cmd': 'x'},
          'createdAt': 5,
        });
        expect(connected.status, MCPStatus.CONNECTED);
        expect(connected.tools, ['read', 'write']);
        expect(connected.detail, 'ok');
        expect(connected.config, {'cmd': 'x'});

        final disc = MCPConnection.fromJson({
          'id': 'm2',
          'name': 'G',
          'type': 'sse',
          'status': 'DISCONNECTED',
          'createdAt': 5,
        });
        expect(disc.status, MCPStatus.DISCONNECTED);
        expect(disc.tools, isEmpty);
        expect(disc.detail, isNull);
        expect(disc.config, isNull);
      },
    );

    test('MCPConnection.toJson and copyWith', () {
      const mcp = MCPConnection(
        id: 'm1',
        name: 'FS',
        type: 'stdio',
        createdAt: 5,
      );
      expect(mcp.status, MCPStatus.DISCONNECTED);
      final copy = mcp.copyWith(status: MCPStatus.CONNECTED, tools: ['t']);
      expect(copy.status, MCPStatus.CONNECTED);
      expect(copy.tools, ['t']);
      expect(copy.name, 'FS');
      expect(copy.toJson()['status'], 'CONNECTED');
      expect(MCPConnection.fromJson(copy.toJson()).tools, ['t']);
    });

    test(
      'AgentDefinition.fromJson reads llmConnectionId; toJson/copyWith work',
      () {
        final agent = AgentDefinition.fromJson({
          'id': 'a',
          'name': 'A',
          'skillId': 'sk',
          'allowedMcpIds': ['m1'],
          'llmConnectionId': 'llm1',
          'createdAt': 1,
          'updatedAt': 2,
        });
        expect(agent.skillId, 'sk');
        expect(agent.allowedMcpIds, ['m1']);
        expect(agent.llmConnectionId, 'llm1');
        expect(agent.toJson()['llmConnectionId'], 'llm1');
        final copy = agent.copyWith(name: 'B', allowedMcpIds: []);
        expect(copy.name, 'B');
        expect(copy.allowedMcpIds, isEmpty);
        expect(copy.skillId, 'sk');
        expect(copy.updatedAt, 2);
        expect(copy.llmConnectionId, 'llm1');
      },
    );

    test('AgentDefinition.copyWith clears nullable fields only when null is '
        'passed explicitly', () {
      const agent = AgentDefinition(
        id: 'a',
        name: 'A',
        skillId: 'sk',
        llmConnectionId: 'llm1',
        createdAt: 1,
        updatedAt: 2,
      );
      expect(agent.copyWith(name: 'B').skillId, 'sk');
      expect(agent.copyWith(name: 'B').llmConnectionId, 'llm1');
      expect(agent.copyWith(skillId: null).skillId, isNull);
      expect(agent.copyWith(skillId: null).llmConnectionId, 'llm1');
      expect(agent.copyWith(llmConnectionId: null).llmConnectionId, isNull);
      expect(agent.copyWith(skillId: 'sk2').skillId, 'sk2');
    });
  });

  group('LlmConnection model', () {
    const sampleJson = {
      'id': 'llm-1',
      'name': 'My OpenAI',
      'baseUrl': 'https://api.openai.com/v1',
      'model': 'gpt-4o',
      'apiKey': 'sk-****',
      'isDefault': true,
      'createdAt': 1700000000000,
    };

    test('LlmConnection.fromJson parses all fields correctly', () {
      final conn = LlmConnection.fromJson(sampleJson);
      expect(conn.id, 'llm-1');
      expect(conn.name, 'My OpenAI');
      expect(conn.baseUrl, 'https://api.openai.com/v1');
      expect(conn.model, 'gpt-4o');
      expect(conn.apiKey, 'sk-****');
      expect(conn.isDefault, isTrue);
      expect(conn.createdAt, 1700000000000);
    });

    test('LlmConnection.fromJson defaults isDefault to false when absent', () {
      final conn = LlmConnection.fromJson({
        'id': 'x',
        'name': 'X',
        'baseUrl': 'http://localhost:11434/v1',
        'model': 'llama3',
        'apiKey': '',
        'createdAt': 1,
      });
      expect(conn.isDefault, isFalse);
    });

    test('LlmConnection.toJson round-trips correctly', () {
      final conn = LlmConnection.fromJson(sampleJson);
      final json = conn.toJson();
      expect(json['id'], 'llm-1');
      expect(json['name'], 'My OpenAI');
      expect(json['baseUrl'], 'https://api.openai.com/v1');
      expect(json['model'], 'gpt-4o');
      expect(json['apiKey'], 'sk-****');
      expect(json['isDefault'], true);
      expect(json['createdAt'], 1700000000000);
      // fromJson(toJson()) round-trip
      final copy = LlmConnection.fromJson(json);
      expect(copy.id, conn.id);
      expect(copy.isDefault, conn.isDefault);
    });

    test('LlmConnection.copyWith updates only specified fields', () {
      final conn = LlmConnection.fromJson(sampleJson);
      final updated = conn.copyWith(name: 'Updated', isDefault: false);
      expect(updated.name, 'Updated');
      expect(updated.isDefault, isFalse);
      // unchanged fields
      expect(updated.id, 'llm-1');
      expect(updated.baseUrl, 'https://api.openai.com/v1');
      expect(updated.model, 'gpt-4o');
      expect(updated.apiKey, 'sk-****');
      expect(updated.createdAt, 1700000000000);
    });

    test('LlmConnection.copyWith with no args returns equivalent object', () {
      final conn = LlmConnection.fromJson(sampleJson);
      final copy = conn.copyWith();
      expect(copy.id, conn.id);
      expect(copy.name, conn.name);
      expect(copy.isDefault, conn.isDefault);
    });
  });
}
