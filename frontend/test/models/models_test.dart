import 'package:flutter_test/flutter_test.dart';
import 'package:loom_ui/models/agent_definition.dart';
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
      expect(agent.roleDescription, isNull);
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

    test('McpConnection.fromJson parses correctly', () {
      final json = {
        'id': 'mcp-1',
        'name': 'Filesystem MCP',
        'type': 'stdio',
        'status': 'CONNECTED',
        'createdAt': 1700000000000,
      };
      final mcp = McpConnection.fromJson(json);
      expect(mcp.id, 'mcp-1');
      expect(mcp.name, 'Filesystem MCP');
      expect(mcp.type, 'stdio');
      expect(mcp.status, McpStatus.connected);
    });

    test('Skill.fromJson parses correctly', () {
      final json = {
        'id': 'skill-1',
        'name': 'Code Review',
        'description': 'Performs automated code reviews',
        'content': 'You are a code reviewer.',
        'tags': 'review,code',
        'createdAt': 1700000000000,
        'updatedAt': 1700000050000,
      };
      final skill = Skill.fromJson(json);
      expect(skill.id, 'skill-1');
      expect(skill.name, 'Code Review');
      expect(skill.description, 'Performs automated code reviews');
      expect(skill.content, 'You are a code reviewer.');
      expect(skill.tags, 'review,code');
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
}
