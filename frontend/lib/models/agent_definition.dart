class AgentDefinition {
  final String id;
  final String name;
  final String roleDescription;
  final String? skillId;
  final List<String> allowedMcpIds;
  final int createdAt;
  final int updatedAt;
  final String? llmConnectionId;

  const AgentDefinition({
    required this.id,
    required this.name,
    this.roleDescription = '',
    this.skillId,
    this.allowedMcpIds = const [],
    required this.createdAt,
    required this.updatedAt,
    this.llmConnectionId,
  });

  factory AgentDefinition.fromJson(Map<String, dynamic> json) {
    final rawIds = json['allowedMcpIds'];
    final ids = rawIds is List ? rawIds.cast<String>() : <String>[];
    return AgentDefinition(
      id: json['id'] as String,
      name: json['name'] as String,
      roleDescription: json['roleDescription'] as String? ?? '',
      skillId: json['skillId'] as String?,
      allowedMcpIds: ids,
      createdAt: (json['createdAt'] as num).toInt(),
      updatedAt: (json['updatedAt'] as num).toInt(),
      llmConnectionId: json['llmConnectionId'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'roleDescription': roleDescription,
    'skillId': skillId,
    'allowedMcpIds': allowedMcpIds,
    'createdAt': createdAt,
    'updatedAt': updatedAt,
    'llmConnectionId': llmConnectionId,
  };

  static const Object _unset = Object();

  /// [skillId] and [llmConnectionId] are nullable: omit to keep the current
  /// value, pass `null` explicitly to clear it.
  AgentDefinition copyWith({
    String? id,
    String? name,
    String? roleDescription,
    Object? skillId = _unset,
    List<String>? allowedMcpIds,
    int? createdAt,
    int? updatedAt,
    Object? llmConnectionId = _unset,
  }) => AgentDefinition(
    id: id ?? this.id,
    name: name ?? this.name,
    roleDescription: roleDescription ?? this.roleDescription,
    skillId: identical(skillId, _unset) ? this.skillId : skillId as String?,
    allowedMcpIds: allowedMcpIds ?? this.allowedMcpIds,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
    llmConnectionId: identical(llmConnectionId, _unset)
        ? this.llmConnectionId
        : llmConnectionId as String?,
  );
}
