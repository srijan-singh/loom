class AgentDefinition {
  final String id;
  final String name;
  final String? roleDescription;
  final String? skillId;
  final List<String> allowedMcpIds;
  final int createdAt;
  final int updatedAt;

  const AgentDefinition({
    required this.id,
    required this.name,
    this.roleDescription,
    this.skillId,
    required this.allowedMcpIds,
    required this.createdAt,
    required this.updatedAt,
  });

  factory AgentDefinition.fromJson(Map<String, dynamic> json) {
    final rawIds = json['allowedMcpIds'];
    final ids = rawIds is List ? rawIds.cast<String>() : <String>[];
    return AgentDefinition(
      id: json['id'] as String,
      name: json['name'] as String,
      roleDescription: json['roleDescription'] as String?,
      skillId: json['skillId'] as String?,
      allowedMcpIds: ids,
      createdAt: (json['createdAt'] as num).toInt(),
      updatedAt: (json['updatedAt'] as num).toInt(),
    );
  }
}
