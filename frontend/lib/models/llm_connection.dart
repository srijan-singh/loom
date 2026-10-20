class LlmConnection {
  final String id;
  final String name;
  final String baseUrl;
  final String model;
  /// API key as returned by the backend — masked (first 3 chars + "****").
  /// Never treat this value as a real key; it is display-only.
  final String apiKey;
  final bool isDefault;
  final int createdAt;

  const LlmConnection({
    required this.id,
    required this.name,
    required this.baseUrl,
    required this.model,
    required this.apiKey,
    required this.isDefault,
    required this.createdAt,
  });

  factory LlmConnection.fromJson(Map<String, dynamic> json) => LlmConnection(
        id: json['id'] as String,
        name: json['name'] as String,
        baseUrl: json['baseUrl'] as String,
        model: json['model'] as String,
        apiKey: json['apiKey'] as String? ?? '',
        isDefault: json['isDefault'] as bool? ?? false,
        createdAt: (json['createdAt'] as num).toInt(),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'baseUrl': baseUrl,
        'model': model,
        'apiKey': apiKey,
        'isDefault': isDefault,
        'createdAt': createdAt,
      };

  LlmConnection copyWith({
    String? id,
    String? name,
    String? baseUrl,
    String? model,
    String? apiKey,
    bool? isDefault,
    int? createdAt,
  }) =>
      LlmConnection(
        id: id ?? this.id,
        name: name ?? this.name,
        baseUrl: baseUrl ?? this.baseUrl,
        model: model ?? this.model,
        apiKey: apiKey ?? this.apiKey,
        isDefault: isDefault ?? this.isDefault,
        createdAt: createdAt ?? this.createdAt,
      );
}
