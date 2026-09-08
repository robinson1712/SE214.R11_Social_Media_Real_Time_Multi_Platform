/// Mirrors user-service's `FollowStatusResponse`.
class FollowStatus {
  final bool following;
  final int followerCount;
  final int followingCount;

  const FollowStatus({
    required this.following,
    required this.followerCount,
    required this.followingCount,
  });

  factory FollowStatus.fromJson(Map<String, dynamic> json) => FollowStatus(
        following: json['following'] as bool? ?? false,
        followerCount: (json['followerCount'] as num?)?.toInt() ?? 0,
        followingCount: (json['followingCount'] as num?)?.toInt() ?? 0,
      );
}
