enum McpStatus { connected, disconnected }

McpStatus _statusFromString(String? s) {
  if (s?.toUpperCase() == 'CONNECTED') return McpStatus.connected;
  return McpStatus.disconnected;
}

class McpConnection {
  final String id;
  final String name;
  final String type;
  final McpStatus status;
  final int createdAt;

  const McpConnection({
    required this.id,
    required this.name,
    required this.type,
    required this.status,
    required this.createdAt,
  });

  factory McpConnection.fromJson(Map<String, dynamic> json) => McpConnection(
        id: json['id'] as String,
        name: json['name'] as String,
        type: json['type'] as String,
        status: _statusFromString(json['status'] as String?),
        createdAt: (json['createdAt'] as num).toInt(),
      );
}
