import 'enums.dart';

/// Mirrors comment-service's `Comment` entity — generalized to
/// (targetType, targetId) rather than a post-only `postId`, since reels
/// have comments too now (see comment-service's CommentCreatedEvent).
class Comment {
  final String id;
  final TargetType targetType;
  final String targetId;
  final String authorId;
  final String targetOwnerId;
  final String? parentCommentId;
  final String content;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  final bool deleted;

  const Comment({
    required this.id,
    required this.targetType,
    required this.targetId,
    required this.authorId,
    required this.targetOwnerId,
    this.parentCommentId,
    required this.content,
    this.createdAt,
    this.updatedAt,
    this.deleted = false,
  });

  bool get isReply => parentCommentId != null;

  factory Comment.fromJson(Map<String, dynamic> json) => Comment(
        id: json['id'] as String,
        targetType: TargetTypeJson.fromJson(json['targetType'] as String?),
        targetId: json['targetId'] as String,
        authorId: json['authorId'] as String,
        targetOwnerId: json['targetOwnerId'] as String,
        parentCommentId: json['parentCommentId'] as String?,
        content: json['content'] as String? ?? '',
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
        updatedAt: json['updatedAt'] == null
            ? null
            : DateTime.tryParse(json['updatedAt'] as String),
        deleted: json['deleted'] as bool? ?? false,
      );
}

class CreateCommentRequest {
  final TargetType targetType;
  final String targetId;
  final String targetOwnerId;
  final String content;
  final String? parentCommentId;

  const CreateCommentRequest({
    required this.targetType,
    required this.targetId,
    required this.targetOwnerId,
    required this.content,
    this.parentCommentId,
  });

  Map<String, dynamic> toJson() => {
        'targetType': targetType.toJson(),
        'targetId': targetId,
        'targetOwnerId': targetOwnerId,
        'content': content,
        'parentCommentId': parentCommentId,
      };
}
