class Skill {
  final String id;
  final String name;
  final String? description;
  final String? content;
  final String? tags;
  final int createdAt;
  final int updatedAt;

  const Skill({
    required this.id,
    required this.name,
    this.description,
    this.content,
    this.tags,
    required this.createdAt,
    required this.updatedAt,
  });

  factory Skill.fromJson(Map<String, dynamic> json) => Skill(
        id: json['id'] as String,
        name: json['name'] as String,
        description: json['description'] as String?,
        content: json['content'] as String?,
        tags: json['tags'] as String?,
        createdAt: (json['createdAt'] as num).toInt(),
        updatedAt: (json['updatedAt'] as num).toInt(),
      );
}
