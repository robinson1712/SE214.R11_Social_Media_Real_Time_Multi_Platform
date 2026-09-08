import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/follow_status.dart';
import '../../core/models/friend_suggestion.dart';
import '../../core/models/friendship.dart';
import '../../core/models/user_profile.dart';
import 'profile_repository.dart';

final viewedProfileProvider =
    FutureProvider.family<UserProfile, String>((ref, userId) {
  return ref.read(profileRepositoryProvider).getById(userId);
});

/// Friend ids of the given user — used to tell whether a viewed profile is
/// already a friend of the current user (call with the current user's own
/// id). Invalidated after a friend-request accept/remove so the button
/// state refreshes.
final friendIdsOfProvider =
    FutureProvider.family<List<String>, String>((ref, userId) {
  return ref.read(profileRepositoryProvider).friendIds(userId);
});

/// The current user's relationship to [targetId] — drives the Facebook-style
/// friend-request button state machine (NONE/PENDING_SENT/PENDING_RECEIVED/
/// FRIENDS) on the profile page.
final friendshipStatusProvider =
    FutureProvider.family<FriendshipStatusModel, String>((ref, targetId) {
  return ref.read(profileRepositoryProvider).friendshipStatus(targetId);
});

final pendingFriendRequestsProvider =
    FutureProvider<List<Friendship>>((ref) async {
  final page = await ref.read(profileRepositoryProvider).myFriendRequests(size: 50);
  return page.content;
});

/// "Người bạn có thể biết" for the right sidebar — ranked by mutual-friend
/// count server-side, with dismissed suggestions hidden for a while (see
/// FriendSuggestionService on the backend for the adaptive cooldown).
final friendSuggestionsProvider =
    FutureProvider<List<FriendSuggestion>>((ref) {
  return ref.read(profileRepositoryProvider).friendSuggestions(limit: 10);
});

/// Shared friend list used to populate `@mention` autocomplete in the post
/// composer and comment boxes, so each place doesn't have to fetch its own.
final myFriendsListProvider = FutureProvider<List<UserProfile>>((ref) async {
  final page = await ref.read(profileRepositoryProvider).myFriends(size: 200);
  return page.content;
});

class FriendshipActions {
  final Ref _ref;

  FriendshipActions(this._ref);

  ProfileRepository get _repository => _ref.read(profileRepositoryProvider);

  Future<void> sendRequest(String targetId) async {
    await _repository.sendFriendRequest(targetId);
    _ref.invalidate(friendshipStatusProvider(targetId));
    // Deliberately NOT invalidating friendSuggestionsProvider here — the
    // suggestions sidebar keeps a person visible with an "Hoàn tác" (undo)
    // button right after you send them a request, so a misclick is
    // recoverable without them vanishing first. It naturally drops off the
    // list (backend excludes anyone with a pending request) next time the
    // suggestions list is actually refetched.
  }

  Future<void> dismissSuggestion(String targetId) async {
    await _repository.dismissSuggestion(targetId);
    _ref.invalidate(friendSuggestionsProvider);
  }

  Future<void> accept(String friendshipId) async {
    await _repository.acceptFriendRequest(friendshipId);
    _ref.invalidate(pendingFriendRequestsProvider);
    // Accepting forms a new friendship — without this, any already-cached
    // friendIdsOfProvider(myId) stays stale, so a profile you just became
    // friends with can keep showing "Kết bạn" and the backend rejects the
    // resulting request as a duplicate.
    _ref.invalidate(friendIdsOfProvider);
    // We only know the friendshipId here, not which profile the caller might
    // be viewing, so invalidate every cached relationship status rather than
    // one specific target.
    _ref.invalidate(friendshipStatusProvider);
  }

  /// Declines an incoming request OR cancels one you sent — same backend
  /// endpoint, same outcome: the pending row is gone and the relationship
  /// reverts to NONE either way.
  Future<void> decline(String friendshipId) async {
    await _repository.declineFriendRequest(friendshipId);
    _ref.invalidate(pendingFriendRequestsProvider);
    _ref.invalidate(friendshipStatusProvider);
  }

  Future<void> removeFriend(String friendId) async {
    await _repository.removeFriend(friendId);
    _ref.invalidate(friendIdsOfProvider);
    _ref.invalidate(friendshipStatusProvider);
  }

  Future<void> block(String targetId) async {
    await _repository.block(targetId);
  }

  Future<void> unblock(String targetId) async {
    await _repository.unblock(targetId);
  }
}

final friendshipActionsProvider = Provider((ref) => FriendshipActions(ref));

/// Follow state for a viewed profile — independent of friendship (see
/// FollowService on the backend for why). Invalidated after follow/unfollow
/// so the button and counts refresh.
final followStatusProvider =
    FutureProvider.family<FollowStatus, String>((ref, targetId) {
  return ref.read(profileRepositoryProvider).followStatus(targetId);
});

class FollowActions {
  final Ref _ref;

  FollowActions(this._ref);

  ProfileRepository get _repository => _ref.read(profileRepositoryProvider);

  Future<void> follow(String targetId) async {
    await _repository.followUser(targetId);
    _ref.invalidate(followStatusProvider(targetId));
  }

  Future<void> unfollow(String targetId) async {
    await _repository.unfollowUser(targetId);
    _ref.invalidate(followStatusProvider(targetId));
  }
}

final followActionsProvider = Provider((ref) => FollowActions(ref));
