import 'enums.dart';

/// Mirrors post-service's `Post` entity. Also used to parse feed-service's
/// trimmed `PostDto` (missing `customAudienceUserIds`/`updatedAt`) — those
/// two fields just fall back to sensible defaults when absent so a single
/// model works for both the feed and the direct post-service responses.
class Post {
  final String id;
  final String authorId;
  final String? content;
  final List<String> mediaUrls;
  final Privacy privacy;
  final List<String> customAudienceUserIds;
  final List<String> taggedUserIds;
  final String? groupId;
  final String? pageId;
  final String? sharedPostId;
  final String? sharedReelId;
  final int commentCount;
  final int reactionCount;
  final bool pinned;
  final int shareCount;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  const Post({
    required this.id,
    required this.authorId,
    this.content,
    this.mediaUrls = const [],
    this.privacy = Privacy.public,
    this.customAudienceUserIds = const [],
    this.taggedUserIds = const [],
    this.groupId,
    this.pageId,
    this.sharedPostId,
    this.sharedReelId,
    this.commentCount = 0,
    this.reactionCount = 0,
    this.pinned = false,
    this.shareCount = 0,
    this.createdAt,
    this.updatedAt,
  });

  bool get isShare => sharedPostId != null || sharedReelId != null;
  bool get hasMedia => mediaUrls.isNotEmpty;

  factory Post.fromJson(Map<String, dynamic> json) {
    List<String> stringList(dynamic raw) =>
        (raw as List<dynamic>? ?? const []).map((e) => e as String).toList();

    return Post(
      id: json['id'] as String,
      authorId: json['authorId'] as String,
      content: json['content'] as String?,
      mediaUrls: stringList(json['mediaUrls']),
      privacy: PrivacyJson.fromJson(json['privacy'] as String?),
      customAudienceUserIds: stringList(json['customAudienceUserIds']),
      taggedUserIds: stringList(json['taggedUserIds']),
      groupId: json['groupId'] as String?,
      pageId: json['pageId'] as String?,
      sharedPostId: json['sharedPostId'] as String?,
      sharedReelId: json['sharedReelId'] as String?,
      commentCount: (json['commentCount'] as num?)?.toInt() ?? 0,
      reactionCount: (json['reactionCount'] as num?)?.toInt() ?? 0,
      pinned: json['pinned'] as bool? ?? false,
      shareCount: (json['shareCount'] as num?)?.toInt() ?? 0,
      createdAt: json['createdAt'] == null
          ? null
          : DateTime.tryParse(json['createdAt'] as String),
      updatedAt: json['updatedAt'] == null
          ? null
          : DateTime.tryParse(json['updatedAt'] as String),
    );
  }
}

class CreatePostRequest {
  final String? content;
  final List<String>? mediaUrls;
  final Privacy? privacy;
  final String? groupId;
  final String? pageId;
  final List<String>? customAudienceUserIds;
  final List<String>? taggedUserIds;

  const CreatePostRequest({
    this.content,
    this.mediaUrls,
    this.privacy,
    this.groupId,
    this.pageId,
    this.customAudienceUserIds,
    this.taggedUserIds,
  });

  Map<String, dynamic> toJson() => {
        'content': content,
        'mediaUrls': mediaUrls,
        if (privacy != null) 'privacy': privacy!.toJson(),
        'groupId': groupId,
        'pageId': pageId,
        'customAudienceUserIds': customAudienceUserIds,
        'taggedUserIds': taggedUserIds,
      };
}

class UpdatePostRequest {
  final String? content;
  final List<String>? mediaUrls;
  final Privacy? privacy;
  final List<String>? customAudienceUserIds;
  final List<String>? taggedUserIds;

  const UpdatePostRequest({
    this.content,
    this.mediaUrls,
    this.privacy,
    this.customAudienceUserIds,
    this.taggedUserIds,
  });

  Map<String, dynamic> toJson() => {
        if (content != null) 'content': content,
        if (mediaUrls != null) 'mediaUrls': mediaUrls,
        if (privacy != null) 'privacy': privacy!.toJson(),
        if (customAudienceUserIds != null)
          'customAudienceUserIds': customAudienceUserIds,
        if (taggedUserIds != null) 'taggedUserIds': taggedUserIds,
      };
}

class ShareRequest {
  final String? content;
  final Privacy? privacy;

  const ShareRequest({this.content, this.privacy});

  Map<String, dynamic> toJson() => {
        'content': content,
        if (privacy != null) 'privacy': privacy!.toJson(),
      };
}
