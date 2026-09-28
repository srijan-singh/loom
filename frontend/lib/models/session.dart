enum SessionStatus { created, running, completed, failed, partial }

SessionStatus _statusFromString(String? s) {
  switch (s?.toUpperCase()) {
    case 'RUNNING':
      return SessionStatus.running;
    case 'COMPLETED':
      return SessionStatus.completed;
    case 'FAILED':
      return SessionStatus.failed;
    case 'PARTIAL':
      return SessionStatus.partial;
    default:
      return SessionStatus.created;
  }
}

class Session {
  final String id;
  final String workspaceId;
  final String workflowDefinitionId;
  final SessionStatus status;
  final int startedAt;
  final int? completedAt;
  final int? agentCount;

  const Session({
    required this.id,
    required this.workspaceId,
    required this.workflowDefinitionId,
    required this.status,
    required this.startedAt,
    this.completedAt,
    this.agentCount,
  });

  factory Session.fromJson(Map<String, dynamic> json) => Session(
        id: json['id'] as String,
        workspaceId: json['workspaceId'] as String,
        workflowDefinitionId: json['workflowDefinitionId'] as String,
        status: _statusFromString(json['status'] as String?),
        startedAt: (json['startedAt'] as num).toInt(),
        completedAt: json['completedAt'] != null
            ? (json['completedAt'] as num).toInt()
            : null,
        agentCount: json['agentCount'] != null
            ? (json['agentCount'] as num).toInt()
            : null,
      );
}
