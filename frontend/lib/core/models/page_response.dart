/// Mirrors common-lib's `PageResponse<T>`:
/// `{ content, page, size, totalElements, totalPages, last }`.
class PageResponse<T> {
  final List<T> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final bool last;

  const PageResponse({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.last,
  });

  static PageResponse<T> empty<T>() => PageResponse<T>(
        content: const [],
        page: 0,
        size: 0,
        totalElements: 0,
        totalPages: 0,
        last: true,
      );

  factory PageResponse.fromJson(
    Map<String, dynamic> json,
    T Function(dynamic json) fromJsonT,
  ) {
    final rawContent = json['content'] as List<dynamic>? ?? const [];
    return PageResponse<T>(
      content: rawContent.map(fromJsonT).toList(),
      page: json['page'] as int? ?? 0,
      size: json['size'] as int? ?? 0,
      totalElements: json['totalElements'] as int? ?? 0,
      totalPages: json['totalPages'] as int? ?? 0,
      last: json['last'] as bool? ?? true,
    );
  }
}
