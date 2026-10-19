import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:loom_ui/models/agent_definition.dart';
import 'package:loom_ui/models/mcp_connection.dart';
import 'package:loom_ui/models/skill.dart';
import 'package:loom_ui/services/api_client.dart';

class SkillsNotifier extends AsyncNotifier<List<Skill>> {
  @override
  Future<List<Skill>> build() async {
    final client = ref.watch(apiClientProvider);
    final raw = await client.getSkills();
    return raw
        .map((e) => Skill.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<Skill> createSkill(Skill skill) async {
    final client = ref.read(apiClientProvider);
    final created = await client.createSkill(skill);
    ref.invalidateSelf();
    return created;
  }

  Future<Skill> updateSkill(String id, Skill skill) async {
    final client = ref.read(apiClientProvider);
    final updated = await client.updateSkill(id, skill);
    ref.invalidateSelf();
    return updated;
  }

  Future<void> deleteSkill(String id) async {
    final client = ref.read(apiClientProvider);
    await client.deleteSkill(id);
    ref.invalidateSelf();
  }
}

final skillsProvider =
    AsyncNotifierProvider<SkillsNotifier, List<Skill>>(SkillsNotifier.new);

class McpsNotifier extends AsyncNotifier<List<MCPConnection>> {
  @override
  Future<List<MCPConnection>> build() async {
    final client = ref.watch(apiClientProvider);
    final raw = await client.getMcps();
    return raw
        .map((e) => MCPConnection.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<MCPConnection> createMcp(Map<String, dynamic> payload) async {
    final client = ref.read(apiClientProvider);
    final created = await client.createMcp(payload);
    ref.invalidateSelf();
    return created;
  }

  Future<void> updateMcpStatus(String id, String status) async {
    final client = ref.read(apiClientProvider);
    await client.updateMcpStatus(id, status);
    ref.invalidateSelf();
  }

  Future<void> deleteMcp(String id) async {
    final client = ref.read(apiClientProvider);
    await client.deleteMcp(id);
    ref.invalidateSelf();
  }
}

final mcpsProvider =
    AsyncNotifierProvider<McpsNotifier, List<MCPConnection>>(McpsNotifier.new);

class AgentsNotifier extends AsyncNotifier<List<AgentDefinition>> {
  @override
  Future<List<AgentDefinition>> build() async {
    final client = ref.watch(apiClientProvider);
    final raw = await client.getAgents();
    return raw
        .map((e) => AgentDefinition.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<AgentDefinition> createAgent(AgentDefinition agent) async {
    final client = ref.read(apiClientProvider);
    final created = await client.createAgent(agent);
    ref.invalidateSelf();
    return created;
  }

  Future<AgentDefinition> updateAgent(String id, AgentDefinition agent) async {
    final client = ref.read(apiClientProvider);
    final updated = await client.updateAgent(id, agent);
    ref.invalidateSelf();
    return updated;
  }

  Future<void> deleteAgent(String id) async {
    final client = ref.read(apiClientProvider);
    await client.deleteAgent(id);
    ref.invalidateSelf();
  }
}

final agentsProvider =
    AsyncNotifierProvider<AgentsNotifier, List<AgentDefinition>>(AgentsNotifier.new);
