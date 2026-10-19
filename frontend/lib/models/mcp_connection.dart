// ignore_for_file: constant_identifier_names
enum MCPStatus { CONNECTED, DISCONNECTED }

MCPStatus _statusFromString(String? s) {
  if (s?.toUpperCase() == 'CONNECTED') return MCPStatus.CONNECTED;
  return MCPStatus.DISCONNECTED;
}

class MCPConnection {
  final String id;
  final String name;
  final String type;
  final MCPStatus status;
  final List<String> tools;
  final String? detail;
  final Map<String, dynamic>? config;
  final int createdAt;

  const MCPConnection({
    required this.id,
    required this.name,
    required this.type,
    this.status = MCPStatus.DISCONNECTED,
    this.tools = const [],
    this.detail,
    this.config,
    required this.createdAt,
  });

  factory MCPConnection.fromJson(Map<String, dynamic> json) {
    final rawTools = json['tools'];
    final rawConfig = json['config'];
    return MCPConnection(
      id: json['id'] as String,
      name: json['name'] as String,
      type: json['type'] as String,
      status: _statusFromString(json['status'] as String?),
      tools: rawTools is List ? rawTools.cast<String>() : const [],
      detail: json['detail'] as String?,
      config: rawConfig is Map ? Map<String, dynamic>.from(rawConfig) : null,
      createdAt: (json['createdAt'] as num).toInt(),
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'type': type,
    'status': status.name,
    'tools': tools,
    'detail': detail,
    'config': config,
    'createdAt': createdAt,
  };

  MCPConnection copyWith({
    String? id,
    String? name,
    String? type,
    MCPStatus? status,
    List<String>? tools,
    String? detail,
    Map<String, dynamic>? config,
    int? createdAt,
  }) => MCPConnection(
    id: id ?? this.id,
    name: name ?? this.name,
    type: type ?? this.type,
    status: status ?? this.status,
    tools: tools ?? this.tools,
    detail: detail ?? this.detail,
    config: config ?? this.config,
    createdAt: createdAt ?? this.createdAt,
  );
}
