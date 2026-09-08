import 'enums.dart';

/// Mirrors user-service's `FriendshipResponse`.
class Friendship {
  final String id;
  final String requesterId;
  final String addresseeId;
  final FriendshipStatus status;
  final DateTime? createdAt;
  final DateTime? respondedAt;

  const Friendship({
    required this.id,
    required this.requesterId,
    required this.addresseeId,
    required this.status,
    this.createdAt,
    this.respondedAt,
  });

  factory Friendship.fromJson(Map<String, dynamic> json) => Friendship(
        id: json['id'] as String,
        requesterId: json['requesterId'] as String,
        addresseeId: json['addresseeId'] as String,
        status: FriendshipStatusJson.fromJson(json['status'] as String?),
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
        respondedAt: json['respondedAt'] == null
            ? null
            : DateTime.tryParse(json['respondedAt'] as String),
      );
}

/// Mirrors user-service's `FriendshipStatusResponse` — the caller's
/// relationship to one specific other user.
class FriendshipStatusModel {
  final RelationshipStatus status;
  final String? friendshipId;

  const FriendshipStatusModel({required this.status, this.friendshipId});

  factory FriendshipStatusModel.fromJson(Map<String, dynamic> json) =>
      FriendshipStatusModel(
        status: RelationshipStatusJson.fromJson(json['status'] as String?),
        friendshipId: json['friendshipId'] as String?,
      );
}
