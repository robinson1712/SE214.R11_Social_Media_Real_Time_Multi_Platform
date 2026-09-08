import 'enums.dart';

/// Mirrors reaction-service's `SavedItem` entity (the "Đã lưu" bookmark
/// feature lives alongside reactions there — see SavedItemController).
class SavedItem {
  final String id;
  final TargetType targetType;
  final String targetId;
  final String? targetOwnerId;
  final DateTime? createdAt;

  const SavedItem({
    required this.id,
    required this.targetType,
    required this.targetId,
    this.targetOwnerId,
    this.createdAt,
  });

  factory SavedItem.fromJson(Map<String, dynamic> json) => SavedItem(
        id: json['id'] as String,
        targetType: TargetTypeJson.fromJson(json['targetType'] as String?),
        targetId: json['targetId'] as String,
        targetOwnerId: json['targetOwnerId'] as String?,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );
}

class SaveItemRequest {
  final TargetType targetType;
  final String targetId;
  final String? targetOwnerId;

  const SaveItemRequest({required this.targetType, required this.targetId, this.targetOwnerId});

  Map<String, dynamic> toJson() => {
        'targetType': targetType.toJson(),
        'targetId': targetId,
        'targetOwnerId': targetOwnerId,
      };
}
