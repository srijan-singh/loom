class Workspace {
  final String id;
  final String name;
  final String? description;
  final int createdAt;

  const Workspace({
    required this.id,
    required this.name,
    this.description,
    required this.createdAt,
  });

  factory Workspace.fromJson(Map<String, dynamic> json) => Workspace(
        id: json['id'] as String,
        name: json['name'] as String,
        description: json['description'] as String?,
        createdAt: (json['createdAt'] as num).toInt(),
      );
}
