import 'user_profile.dart';

/// Mirrors user-service's `FriendSuggestionResponse`.
class FriendSuggestion {
  final UserProfile profile;
  final int mutualFriendCount;

  const FriendSuggestion({required this.profile, required this.mutualFriendCount});

  factory FriendSuggestion.fromJson(Map<String, dynamic> json) => FriendSuggestion(
        profile: UserProfile.fromJson(json['profile'] as Map<String, dynamic>),
        mutualFriendCount: (json['mutualFriendCount'] as num?)?.toInt() ?? 0,
      );
}
