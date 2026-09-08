import 'enums.dart';

/// Mirrors reaction-service's `Reaction` entity.
class Reaction {
  final String id;
  final TargetType targetType;
  final String targetId;
  final String? targetOwnerId;
  final String userId;
  final ReactionType type;
  final DateTime? createdAt;

  const Reaction({
    required this.id,
    required this.targetType,
    required this.targetId,
    this.targetOwnerId,
    required this.userId,
    required this.type,
    this.createdAt,
  });

  factory Reaction.fromJson(Map<String, dynamic> json) => Reaction(
        id: json['id'] as String,
        targetType: _targetTypeFromJson(json['targetType'] as String?),
        targetId: json['targetId'] as String,
        targetOwnerId: json['targetOwnerId'] as String?,
        userId: json['userId'] as String,
        type: ReactionTypeJson.fromJson(json['type'] as String?) ??
            ReactionType.like,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );

  static TargetType _targetTypeFromJson(String? value) {
    switch (value) {
      case 'COMMENT':
        return TargetType.comment;
      case 'REEL':
        return TargetType.reel;
      case 'STORY':
        return TargetType.story;
      case 'POST':
      default:
        return TargetType.post;
    }
  }
}

/// Mirrors reaction-service's `ReactionSummaryResponse`: `{ counts, total }`,
/// where `counts` is a map keyed by the uppercase enum name.
class ReactionSummary {
  final Map<ReactionType, int> counts;
  final int total;

  const ReactionSummary({required this.counts, required this.total});

  static ReactionSummary empty() =>
      const ReactionSummary(counts: {}, total: 0);

  factory ReactionSummary.fromJson(Map<String, dynamic> json) {
    final rawCounts = json['counts'] as Map<String, dynamic>? ?? const {};
    final counts = <ReactionType, int>{};
    rawCounts.forEach((key, value) {
      final type = ReactionTypeJson.fromJson(key);
      if (type != null) {
        counts[type] = (value as num).toInt();
      }
    });
    return ReactionSummary(
      counts: counts,
      total: (json['total'] as num?)?.toInt() ?? 0,
    );
  }
}

class UpsertReactionRequest {
  final TargetType targetType;
  final String targetId;
  final String targetOwnerId;
  final ReactionType type;

  const UpsertReactionRequest({
    required this.targetType,
    required this.targetId,
    required this.targetOwnerId,
    required this.type,
  });

  Map<String, dynamic> toJson() => {
        'targetType': targetType.toJson(),
        'targetId': targetId,
        'targetOwnerId': targetOwnerId,
        'type': type.toJson(),
      };
}
