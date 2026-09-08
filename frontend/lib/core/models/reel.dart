/// Mirrors reels-service's `Reel` document.
class Reel {
  final String id;
  final String authorId;
  final String videoUrl;
  final String? thumbnailUrl;
  final String? caption;
  final int viewCount;
  final int commentCount;
  final int reactionCount;
  final int shareCount;
  final DateTime? createdAt;

  const Reel({
    required this.id,
    required this.authorId,
    required this.videoUrl,
    this.thumbnailUrl,
    this.caption,
    this.viewCount = 0,
    this.commentCount = 0,
    this.reactionCount = 0,
    this.shareCount = 0,
    this.createdAt,
  });

  factory Reel.fromJson(Map<String, dynamic> json) => Reel(
        id: json['id'] as String,
        authorId: json['authorId'] as String,
        videoUrl: json['videoUrl'] as String? ?? '',
        thumbnailUrl: json['thumbnailUrl'] as String?,
        caption: json['caption'] as String?,
        viewCount: (json['viewCount'] as num?)?.toInt() ?? 0,
        commentCount: (json['commentCount'] as num?)?.toInt() ?? 0,
        reactionCount: (json['reactionCount'] as num?)?.toInt() ?? 0,
        shareCount: (json['shareCount'] as num?)?.toInt() ?? 0,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );
}

class CreateReelRequest {
  final String videoUrl;
  final String? thumbnailUrl;
  final String? caption;

  const CreateReelRequest({
    required this.videoUrl,
    this.thumbnailUrl,
    this.caption,
  });

  Map<String, dynamic> toJson() => {
        'videoUrl': videoUrl,
        'thumbnailUrl': thumbnailUrl,
        'caption': caption,
      };
}
