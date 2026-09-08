/// Mirrors search-service's DTOs.
class SearchUserResult {
  final String id;
  final String fullName;
  final String? avatarUrl;

  const SearchUserResult({required this.id, required this.fullName, this.avatarUrl});

  factory SearchUserResult.fromJson(Map<String, dynamic> json) => SearchUserResult(
        id: json['id'] as String,
        fullName: json['fullName'] as String? ?? '',
        avatarUrl: json['avatarUrl'] as String?,
      );
}

class SearchPostResult {
  final String id;
  final String authorId;
  final String? content;

  const SearchPostResult({required this.id, required this.authorId, this.content});

  factory SearchPostResult.fromJson(Map<String, dynamic> json) => SearchPostResult(
        id: json['id'] as String,
        authorId: json['authorId'] as String,
        content: json['content'] as String?,
      );
}

class SearchGroupResult {
  final String id;
  final String name;
  final String? avatarUrl;
  final String? description;

  const SearchGroupResult({required this.id, required this.name, this.avatarUrl, this.description});

  factory SearchGroupResult.fromJson(Map<String, dynamic> json) => SearchGroupResult(
        id: json['id'] as String,
        name: json['name'] as String? ?? '',
        avatarUrl: json['avatarUrl'] as String?,
        description: json['description'] as String?,
      );
}

class SearchPageResult {
  final String id;
  final String name;
  final String? avatarUrl;
  final String? category;

  const SearchPageResult({required this.id, required this.name, this.avatarUrl, this.category});

  factory SearchPageResult.fromJson(Map<String, dynamic> json) => SearchPageResult(
        id: json['id'] as String,
        name: json['name'] as String? ?? '',
        avatarUrl: json['avatarUrl'] as String?,
        category: json['category'] as String?,
      );
}

class SearchResults {
  final List<SearchUserResult> users;
  final List<SearchGroupResult> groups;
  final List<SearchPageResult> pages;
  final List<SearchPostResult> posts;

  const SearchResults({
    this.users = const [],
    this.groups = const [],
    this.pages = const [],
    this.posts = const [],
  });

  bool get isEmpty => users.isEmpty && groups.isEmpty && pages.isEmpty && posts.isEmpty;

  factory SearchResults.fromJson(Map<String, dynamic> json) => SearchResults(
        users: (json['users'] as List<dynamic>? ?? const [])
            .map((e) => SearchUserResult.fromJson(e as Map<String, dynamic>))
            .toList(),
        groups: (json['groups'] as List<dynamic>? ?? const [])
            .map((e) => SearchGroupResult.fromJson(e as Map<String, dynamic>))
            .toList(),
        pages: (json['pages'] as List<dynamic>? ?? const [])
            .map((e) => SearchPageResult.fromJson(e as Map<String, dynamic>))
            .toList(),
        posts: (json['posts'] as List<dynamic>? ?? const [])
            .map((e) => SearchPostResult.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
