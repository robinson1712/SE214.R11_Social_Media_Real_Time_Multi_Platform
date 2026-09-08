import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/models/enums.dart';
import '../../core/models/friendship.dart';
import '../../core/models/page_response.dart';
import '../../core/models/post.dart';
import '../../core/models/story.dart';
import '../../core/models/user_profile.dart';
import '../../shared/widgets/avatar.dart';
import '../../shared/widgets/post_card.dart';
import '../auth/auth_provider.dart';
import '../auth/current_user_widgets.dart';
import '../chat/chat_provider.dart';
import '../posts/post_composer_dialog.dart';
import '../posts/post_provider.dart';
import '../stories/story_repository.dart';
import '../stories/story_viewer.dart';
import 'edit_profile_page.dart';
import 'profile_provider.dart';
import 'user_lookup_provider.dart';

class ProfilePage extends ConsumerStatefulWidget {
  final String userId;

  const ProfilePage({super.key, required this.userId});

  @override
  ConsumerState<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends ConsumerState<ProfilePage> {
  @override
  Widget build(BuildContext context) {
    final myId = ref.watch(currentAccountIdProvider);
    final isMe = myId == widget.userId;
    final profileAsync = ref.watch(viewedProfileProvider(widget.userId));

    // No page-level AppBar here — AppShell already renders one persistent
    // top bar for the whole app; a second one just for this page created a
    // redundant, empty-feeling "← Trang cá nhân" strip stacked under it.
    // Facebook's own profile page doesn't have one either — back
    // navigation is just the browser's back button (works correctly now
    // that go_router's imperative pushes sync to it — see app_router.dart).
    return profileAsync.when(
        data: (profile) => Center(
          child: LayoutBuilder(
            builder: (context, constraints) {
              // Facebook-clone profile is deliberately wider than the
              // single-column feed — 2.5/3.5 of whatever width the shell's
              // center column gives this page.
              final contentWidth = constraints.maxWidth * (2.5 / 3.5);
              return SizedBox(
                width: contentWidth,
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      _CoverHeader(
                        profile: profile,
                        userId: widget.userId,
                        isMe: isMe,
                        myId: myId,
                      ),
                      const SizedBox(height: 16),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Expanded(
                            flex: 2,
                            child: _LeftColumn(profile: profile, userId: widget.userId),
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            flex: 3,
                            child: _RightColumn(
                                profile: profile, userId: widget.userId, isMe: isMe),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Padding(
        padding: const EdgeInsets.all(24),
        child: Text('Không tải được trang cá nhân: $e'),
      ),
    );
  }
}

/// Cover photo + overlapping avatar + name/counts/bio + action row, all
/// Facebook-style — the header band that sits above the two-column layout.
class _CoverHeader extends StatelessWidget {
  final UserProfile profile;
  final String userId;
  final bool isMe;
  final String? myId;

  const _CoverHeader({
    required this.profile,
    required this.userId,
    required this.isMe,
    required this.myId,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Stack(
            clipBehavior: Clip.none,
            children: [
              profile.coverUrl != null
                  ? Image.network(profile.coverUrl!,
                      height: 300, width: double.infinity, fit: BoxFit.cover)
                  : Container(height: 220, color: Colors.grey.shade300),
              Positioned(
                left: 24,
                bottom: -48,
                child: Container(
                  padding: const EdgeInsets.all(4),
                  decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
                  child: Avatar(
                      url: profile.avatarUrl,
                      name: profile.fullName,
                      radius: 60,
                      gender: profile.gender),
                ),
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 56, 24, 16),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(profile.fullName, style: Theme.of(context).textTheme.headlineSmall),
                      const SizedBox(height: 4),
                      _FollowCounts(targetId: userId),
                      if (profile.bio != null && profile.bio!.isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.only(top: 8),
                          child: Text(profile.bio!),
                        ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                if (isMe)
                  ElevatedButton.icon(
                    icon: const Icon(Icons.edit),
                    label: const Text('Chỉnh sửa trang cá nhân'),
                    onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute(builder: (_) => EditProfilePage(profile: profile)),
                    ),
                  )
                else
                  _FriendshipActionsRow(myId: myId, targetId: userId),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Left 2/5 column: Giới thiệu / Tin nổi bật / Bạn bè / Ảnh — the info-card
/// stack Facebook shows to the left of a profile's posts.
class _LeftColumn extends StatelessWidget {
  final UserProfile profile;
  final String userId;

  const _LeftColumn({required this.profile, required this.userId});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _InfoCard(profile: profile),
        const SizedBox(height: 12),
        _HighlightsCard(userId: userId),
        const SizedBox(height: 12),
        _FriendsPreviewCard(userId: userId),
        const SizedBox(height: 12),
        _PhotosPreviewCard(userId: userId),
      ],
    );
  }
}

class _SectionCard extends StatelessWidget {
  final String title;
  final Widget? trailing;
  final Widget child;

  const _SectionCard({required this.title, this.trailing, required this.child});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                    child: Text(title, style: Theme.of(context).textTheme.titleMedium)),
                if (trailing != null) trailing!,
              ],
            ),
            const SizedBox(height: 10),
            child,
          ],
        ),
      ),
    );
  }
}

/// "Giới thiệu" — the static facts a profile has: bio, location, birthday.
class _InfoCard extends StatelessWidget {
  final UserProfile profile;

  const _InfoCard({required this.profile});

  @override
  Widget build(BuildContext context) {
    final rows = <Widget>[];
    if (profile.bio != null && profile.bio!.isNotEmpty) {
      rows.add(Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Text(profile.bio!),
      ));
    }
    if (profile.workplace != null && profile.workplace!.isNotEmpty) {
      rows.add(_InfoRow(icon: Icons.work_outline, text: profile.workplace!));
    }
    if (profile.location != null && profile.location!.isNotEmpty) {
      rows.add(_InfoRow(icon: Icons.home_outlined, text: 'Sống ở ${profile.location}'));
    }
    if (profile.dob != null) {
      rows.add(_InfoRow(
          icon: Icons.cake_outlined,
          text: 'Sinh ngày ${DateFormat('dd/MM/yyyy').format(profile.dob!)}'));
    }
    if (profile.createdAt != null) {
      rows.add(_InfoRow(
          icon: Icons.schedule,
          text: 'Tham gia TSON từ ${DateFormat('MM/yyyy').format(profile.createdAt!)}'));
    }

    return _SectionCard(
      title: 'Giới thiệu',
      child: rows.isEmpty
          ? Text('Chưa có thông tin', style: TextStyle(color: Colors.grey.shade600))
          : Column(crossAxisAlignment: CrossAxisAlignment.start, children: rows),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String text;

  const _InfoRow({required this.icon, required this.text});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(icon, size: 18, color: Colors.grey.shade600),
          const SizedBox(width: 10),
          Expanded(child: Text(text)),
        ],
      ),
    );
  }
}

/// "Tin nổi bật" — this profile's currently-active (not-yet-expired)
/// stories, tap to open the same story viewer used on the feed strip.
class _HighlightsCard extends ConsumerWidget {
  final String userId;

  const _HighlightsCard({required this.userId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final storiesAsync = ref.watch(_userStoriesProvider(userId));
    final stories = storiesAsync.value ?? const [];
    if (storiesAsync.isLoading && stories.isEmpty) {
      return const _SectionCard(
        title: 'Tin nổi bật',
        child: Center(
            child: SizedBox(
                height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2))),
      );
    }
    if (stories.isEmpty) return const SizedBox.shrink();

    return _SectionCard(
      title: 'Tin nổi bật',
      child: SizedBox(
        height: 96,
        child: ListView.separated(
          scrollDirection: Axis.horizontal,
          itemCount: stories.length,
          separatorBuilder: (_, __) => const SizedBox(width: 8),
          itemBuilder: (context, i) => GestureDetector(
            onTap: () => showStoryViewer(context, stories, i),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: stories[i].mediaType == StoryMediaType.image
                  ? Image.network(stories[i].mediaUrl, width: 64, height: 96, fit: BoxFit.cover)
                  : Container(
                      width: 64,
                      height: 96,
                      color: Colors.black87,
                      child: const Icon(Icons.play_circle_outline, color: Colors.white),
                    ),
            ),
          ),
        ),
      ),
    );
  }
}

final _userStoriesProvider =
    FutureProvider.family<List<Story>, String>((ref, userId) {
  return ref.read(storyRepositoryProvider).byAuthor(userId);
});

/// "Bạn bè" — friend count + a small avatar preview grid.
class _FriendsPreviewCard extends ConsumerWidget {
  final String userId;

  const _FriendsPreviewCard({required this.userId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final idsAsync = ref.watch(friendIdsOfProvider(userId));
    final ids = idsAsync.value ?? const [];

    return _SectionCard(
      title: 'Bạn bè',
      trailing: TextButton(
        onPressed: () => context.push('/friends'),
        child: const Text('Xem tất cả'),
      ),
      child: ids.isEmpty
          ? Text('Chưa có bạn bè', style: TextStyle(color: Colors.grey.shade600))
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('${ids.length} bạn bè', style: TextStyle(color: Colors.grey.shade600)),
                const SizedBox(height: 8),
                GridView.count(
                  crossAxisCount: 3,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  crossAxisSpacing: 8,
                  mainAxisSpacing: 8,
                  childAspectRatio: 0.85,
                  children: ids
                      .take(9)
                      .map((id) => _FriendPreviewTile(userId: id))
                      .toList(),
                ),
              ],
            ),
    );
  }
}

class _FriendPreviewTile extends ConsumerWidget {
  final String userId;

  const _FriendPreviewTile({required this.userId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(userLookupProvider(userId)).value;
    return GestureDetector(
      onTap: () => context.push('/profile/$userId'),
      child: Column(
        children: [
          Avatar(
              url: profile?.avatarUrl,
              name: profile?.fullName ?? '',
              radius: 28,
              gender: profile?.gender),
          const SizedBox(height: 4),
          Text(
            profile?.fullName ?? '',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 11),
          ),
        ],
      ),
    );
  }
}

/// "Ảnh" — a small preview grid of media pulled from this profile's own
/// posts (no separate photos endpoint exists, so this reuses the same
/// author-posts fetch the post list below already needs).
class _PhotosPreviewCard extends ConsumerWidget {
  final String userId;

  const _PhotosPreviewCard({required this.userId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final postsAsync = ref.watch(_userPostsProvider(userId));
    final photos = (postsAsync.value?.content ?? const <Post>[])
        .expand((p) => p.mediaUrls)
        .take(9)
        .toList();

    if (photos.isEmpty) return const SizedBox.shrink();

    return _SectionCard(
      title: 'Ảnh',
      child: GridView.count(
        crossAxisCount: 3,
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        crossAxisSpacing: 6,
        mainAxisSpacing: 6,
        children: photos
            .map((url) => ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: Image.network(url, fit: BoxFit.cover),
                ))
            .toList(),
      ),
    );
  }
}

/// Right 3/5 column: composer (own profile only) + the post/share feed.
class _RightColumn extends StatelessWidget {
  final UserProfile profile;
  final String userId;
  final bool isMe;

  const _RightColumn({required this.profile, required this.userId, required this.isMe});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (isMe) ...[
          _ComposerBox(profile: profile, userId: userId),
          const SizedBox(height: 12),
        ],
        Text('Bài viết', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        _UserPosts(userId: userId),
      ],
    );
  }
}

class _ComposerBox extends ConsumerWidget {
  final UserProfile profile;
  final String userId;

  const _ComposerBox({required this.profile, required this.userId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            const CurrentUserAvatar(),
            const SizedBox(width: 10),
            Expanded(
              child: InkWell(
                borderRadius: BorderRadius.circular(20),
                onTap: () => showPostComposerDialog(
                  context,
                  onPosted: () => ref.invalidate(_userPostsProvider(userId)),
                ),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF0F2F5),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text('Hãy viết gì đó cho ${profile.fullName.split(' ').last}...',
                      style: TextStyle(color: Colors.grey.shade700)),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Facebook-style friend-request button, driven by [friendshipStatusProvider]:
/// NONE -> "Kết bạn"; PENDING_SENT -> "Đã gửi lời mời" (tap to cancel);
/// PENDING_RECEIVED -> Accept/Decline; FRIENDS -> "Bạn bè" (tap to unfriend).
class _FriendshipActionsRow extends ConsumerStatefulWidget {
  final String? myId;
  final String targetId;

  const _FriendshipActionsRow({required this.myId, required this.targetId});

  @override
  ConsumerState<_FriendshipActionsRow> createState() => _FriendshipActionsRowState();
}

class _FriendshipActionsRowState extends ConsumerState<_FriendshipActionsRow> {
  bool _busy = false;

  Future<void> _run(Future<void> Function() action) async {
    if (_busy) return;
    setState(() => _busy = true);
    await _runSafely(context, action);
    if (mounted) setState(() => _busy = false);
  }

  Future<bool> _confirm(String title, String content) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(title),
        content: Text(content),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('Huỷ')),
          TextButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('Xác nhận')),
        ],
      ),
    );
    return confirmed == true;
  }

  @override
  Widget build(BuildContext context) {
    if (widget.myId == null) return const SizedBox.shrink();
    final statusAsync = ref.watch(friendshipStatusProvider(widget.targetId));

    return statusAsync.when(
      data: (status) => Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          _buildFriendshipButton(status),
          _FollowButton(targetId: widget.targetId),
          OutlinedButton.icon(
            icon: const Icon(Icons.chat_bubble_outline),
            label: const Text('Nhắn tin'),
            onPressed: () => _runSafely(context, () async {
              final conversation = await ref
                  .read(chatProvider.notifier)
                  .startConversationWith(widget.targetId);
              if (context.mounted) context.go('/chat/${conversation.id}');
            }),
          ),
          OutlinedButton.icon(
            icon: const Icon(Icons.block),
            label: const Text('Chặn'),
            onPressed: () => _runSafely(context,
                () => ref.read(friendshipActionsProvider).block(widget.targetId)),
          ),
        ],
      ),
      loading: () => const SizedBox(
          height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2)),
      error: (_, __) => const SizedBox.shrink(),
    );
  }

  Widget _buildFriendshipButton(FriendshipStatusModel status) {
    switch (status.status) {
      case RelationshipStatus.friends:
        return OutlinedButton.icon(
          icon: const Icon(Icons.people),
          label: const Text('Bạn bè'),
          onPressed: _busy
              ? null
              : () async {
                  final confirmed = await _confirm(
                    'Huỷ kết bạn?',
                    'Hai bạn sẽ không còn là bạn bè trên TSON nữa.',
                  );
                  if (confirmed) {
                    await _run(() =>
                        ref.read(friendshipActionsProvider).removeFriend(widget.targetId));
                  }
                },
        );

      case RelationshipStatus.pendingSent:
        return OutlinedButton.icon(
          icon: const Icon(Icons.schedule_send),
          label: Text(_busy ? 'Đang xử lý...' : 'Đã gửi lời mời'),
          onPressed: _busy
              ? null
              : () async {
                  final confirmed = await _confirm(
                    'Huỷ lời mời kết bạn?',
                    'Lời mời kết bạn đã gửi sẽ bị huỷ.',
                  );
                  if (confirmed) {
                    await _run(() => ref
                        .read(friendshipActionsProvider)
                        .decline(status.friendshipId!));
                  }
                },
        );

      case RelationshipStatus.pendingReceived:
        return Wrap(
          spacing: 8,
          children: [
            ElevatedButton.icon(
              icon: const Icon(Icons.check),
              label: const Text('Chấp nhận'),
              onPressed: _busy
                  ? null
                  : () => _run(() =>
                      ref.read(friendshipActionsProvider).accept(status.friendshipId!)),
            ),
            OutlinedButton.icon(
              icon: const Icon(Icons.close),
              label: const Text('Từ chối'),
              onPressed: _busy
                  ? null
                  : () => _run(() =>
                      ref.read(friendshipActionsProvider).decline(status.friendshipId!)),
            ),
          ],
        );

      case RelationshipStatus.none:
        return ElevatedButton.icon(
          icon: const Icon(Icons.person_add),
          label: Text(_busy ? 'Đang gửi...' : 'Kết bạn'),
          onPressed: _busy
              ? null
              : () => _run(
                  () => ref.read(friendshipActionsProvider).sendRequest(widget.targetId)),
        );
    }
  }
}

class _FollowCounts extends ConsumerWidget {
  final String targetId;

  const _FollowCounts({required this.targetId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final statusAsync = ref.watch(followStatusProvider(targetId));
    final status = statusAsync.value;
    if (status == null) return const SizedBox.shrink();

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text('${status.followerCount}',
            style: const TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(width: 4),
        Text('người theo dõi', style: TextStyle(color: Colors.grey.shade600)),
        const SizedBox(width: 16),
        Text('${status.followingCount}',
            style: const TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(width: 4),
        Text('đang theo dõi', style: TextStyle(color: Colors.grey.shade600)),
      ],
    );
  }
}

/// Follow/Unfollow — independent of friendship: you can follow a stranger,
/// or unfollow a friend without unfriending them.
class _FollowButton extends ConsumerWidget {
  final String targetId;

  const _FollowButton({required this.targetId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final statusAsync = ref.watch(followStatusProvider(targetId));
    final following = statusAsync.value?.following ?? false;

    return following
        ? OutlinedButton.icon(
            icon: const Icon(Icons.remove_circle_outline),
            label: const Text('Bỏ theo dõi'),
            onPressed: () =>
                _runSafely(context, () => ref.read(followActionsProvider).unfollow(targetId)),
          )
        : ElevatedButton.icon(
            icon: const Icon(Icons.add_circle_outline),
            label: const Text('Theo dõi'),
            onPressed: () =>
                _runSafely(context, () => ref.read(followActionsProvider).follow(targetId)),
          );
  }
}

class _UserPosts extends ConsumerWidget {
  final String userId;

  const _UserPosts({required this.userId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncPosts = ref.watch(_userPostsProvider(userId));
    return asyncPosts.when(
      data: (page) {
        if (page.content.isEmpty) {
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 20),
            child: Text('Chưa có bài viết nào',
                style: TextStyle(color: Colors.grey.shade600)),
          );
        }
        return Column(
          children: page.content
              .map((p) => PostCard(
                    key: ValueKey(p.id),
                    post: p,
                    onDeleted: () => ref.invalidate(_userPostsProvider(userId)),
                    onUpdated: () => ref.invalidate(_userPostsProvider(userId)),
                  ))
              .toList(),
        );
      },
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 20),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (e, _) => Text('Không tải được bài viết: $e'),
    );
  }
}

/// Runs [action] and shows a SnackBar instead of letting a failure (e.g.
/// the backend rejecting a duplicate friend request) become an uncaught
/// error that reaches the app's global crash handler.
Future<void> _runSafely(BuildContext context, Future<void> Function() action) async {
  try {
    await action();
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    }
  }
}

final _userPostsProvider =
    FutureProvider.family<PageResponse<Post>, String>((ref, userId) {
  return ref.read(postRepositoryProvider).byAuthor(userId);
});
