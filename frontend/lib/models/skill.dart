class Skill {
  final String id;
  final String name;
  final String description;
  final String content;
  final List<String> tags;
  final int createdAt;
  final int updatedAt;

  const Skill({
    required this.id,
    required this.name,
    this.description = '',
    this.content = '',
    this.tags = const [],
    required this.createdAt,
    required this.updatedAt,
  });

  factory Skill.fromJson(Map<String, dynamic> json) {
    final rawTags = json['tags'];
    return Skill(
      id: json['id'] as String,
      name: json['name'] as String,
      description: json['description'] as String? ?? '',
      content: json['content'] as String? ?? '',
      tags: rawTags is List ? rawTags.cast<String>() : const [],
      createdAt: (json['createdAt'] as num).toInt(),
      updatedAt: (json['updatedAt'] as num).toInt(),
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'description': description,
    'content': content,
    'tags': tags,
    'createdAt': createdAt,
    'updatedAt': updatedAt,
  };

  Skill copyWith({
    String? id,
    String? name,
    String? description,
    String? content,
    List<String>? tags,
    int? createdAt,
    int? updatedAt,
  }) => Skill(
    id: id ?? this.id,
    name: name ?? this.name,
    description: description ?? this.description,
    content: content ?? this.content,
    tags: tags ?? this.tags,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
  );
}
