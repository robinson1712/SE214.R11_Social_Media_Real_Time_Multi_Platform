import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/models/friend_suggestion.dart';
import '../../core/models/user_profile.dart';
import '../../features/chat/chat_provider.dart';
import '../../features/profile/profile_provider.dart';
import '../../features/profile/profile_repository.dart';
import 'avatar.dart';

/// Facebook-style right sidebar: friends currently online on top, friend
/// suggestions (ranked by mutual-friend count server-side) below.
class RightSidebar extends StatelessWidget {
  const RightSidebar({super.key});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: const [
          _SectionTitle('Đang hoạt động'),
          SizedBox(height: 8),
          _OnlineFriendsSection(),
          SizedBox(height: 24),
          _SectionTitle('Gợi ý kết bạn'),
          SizedBox(height: 8),
          _FriendSuggestionsSection(),
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);

  @override
  Widget build(BuildContext context) {
    return Text(text,
        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15, color: Colors.grey.shade700));
  }
}

Future<void> _runSafely(BuildContext context, Future<void> Function() action) async {
  try {
    await action();
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    }
  }
}

class _OnlineFriendsSection extends ConsumerWidget {
  const _OnlineFriendsSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final friendsAsync = ref.watch(onlineFriendsProvider);

    return friendsAsync.when(
      data: (friends) => friends.isEmpty
          ? Text('Không có bạn bè nào đang hoạt động',
              style: TextStyle(fontSize: 12, color: Colors.grey.shade500))
          : Column(children: friends.map((f) => _OnlineFriendTile(profile: f)).toList()),
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 8),
        child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
      ),
      error: (_, __) => const SizedBox.shrink(),
    );
  }
}

class _OnlineFriendTile extends StatelessWidget {
  final UserProfile profile;
  const _OnlineFriendTile({required this.profile});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () => context.push('/profile/${profile.id}'),
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Row(
          children: [
            Stack(
              clipBehavior: Clip.none,
              children: [
                Avatar(
                    url: profile.avatarUrl,
                    name: profile.fullName,
                    radius: 16,
                    gender: profile.gender),
                Positioned(
                  right: -1,
                  bottom: -1,
                  child: Container(
                    width: 10,
                    height: 10,
                    decoration: BoxDecoration(
                      color: Colors.green,
                      shape: BoxShape.circle,
                      border: Border.all(color: Colors.white, width: 2),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Text(profile.fullName,
                  maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13)),
            ),
          ],
        ),
      ),
    );
  }
}

class _FriendSuggestionsSection extends ConsumerWidget {
  const _FriendSuggestionsSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final suggestionsAsync = ref.watch(friendSuggestionsProvider);

    return suggestionsAsync.when(
      data: (list) => list.isEmpty
          ? Text('Chưa có gợi ý nào', style: TextStyle(fontSize: 12, color: Colors.grey.shade500))
          : Column(children: list.map((s) => _SuggestionTile(suggestion: s)).toList()),
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 8),
        child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
      ),
      error: (_, __) => const SizedBox.shrink(),
    );
  }
}

class _SuggestionTile extends ConsumerStatefulWidget {
  final FriendSuggestion suggestion;
  const _SuggestionTile({required this.suggestion});

  @override
  ConsumerState<_SuggestionTile> createState() => _SuggestionTileState();
}

class _SuggestionTileState extends ConsumerState<_SuggestionTile> {
  bool _busy = false;

  /// The just-sent request's id, kept locally so tapping again cancels it
  /// (Facebook-style "Hoàn tác") without the person vanishing from the list
  /// first — the suggestions list is deliberately not refetched on send
  /// (see FriendshipActions.sendRequest) so this state has something to undo.
  String? _sentFriendshipId;

  ProfileRepository get _repository => ref.read(profileRepositoryProvider);

  Future<void> _sendRequest(String targetId) async {
    setState(() => _busy = true);
    await _runSafely(context, () async {
      final friendship = await _repository.sendFriendRequest(targetId);
      ref.invalidate(friendshipStatusProvider(targetId));
      if (mounted) setState(() => _sentFriendshipId = friendship.id);
    });
    if (mounted) setState(() => _busy = false);
  }

  Future<void> _undoRequest(String targetId) async {
    final friendshipId = _sentFriendshipId;
    if (friendshipId == null) return;
    setState(() => _busy = true);
    await _runSafely(context, () async {
      await _repository.declineFriendRequest(friendshipId);
      ref.invalidate(friendshipStatusProvider(targetId));
      if (mounted) setState(() => _sentFriendshipId = null);
    });
    if (mounted) setState(() => _busy = false);
  }

  @override
  Widget build(BuildContext context) {
    final p = widget.suggestion.profile;
    final sent = _sentFriendshipId != null;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          GestureDetector(
            onTap: () => context.push('/profile/${p.id}'),
            child: Avatar(url: p.avatarUrl, name: p.fullName, radius: 20, gender: p.gender),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                GestureDetector(
                  onTap: () => context.push('/profile/${p.id}'),
                  child: Text(p.fullName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                ),
                if (widget.suggestion.mutualFriendCount > 0)
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Text('${widget.suggestion.mutualFriendCount} bạn chung',
                        style: TextStyle(fontSize: 11, color: Colors.grey.shade600)),
                  ),
                const SizedBox(height: 6),
                SizedBox(
                  height: 30,
                  width: double.infinity,
                  child: sent
                      ? OutlinedButton(
                          style: OutlinedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 10),
                            textStyle: const TextStyle(fontSize: 12),
                          ),
                          onPressed: _busy ? null : () => _undoRequest(p.id),
                          child: Text(_busy ? 'Đang huỷ...' : 'Hoàn tác'),
                        )
                      : ElevatedButton(
                          style: ElevatedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 10),
                            textStyle: const TextStyle(fontSize: 12),
                          ),
                          onPressed: _busy ? null : () => _sendRequest(p.id),
                          child: Text(_busy ? 'Đang gửi...' : 'Thêm bạn bè'),
                        ),
                ),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.close, size: 16),
            tooltip: 'Xoá gợi ý',
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 24, minHeight: 24),
            onPressed: () => _runSafely(
                context, () => ref.read(friendshipActionsProvider).dismissSuggestion(p.id)),
          ),
        ],
      ),
    );
  }
}
