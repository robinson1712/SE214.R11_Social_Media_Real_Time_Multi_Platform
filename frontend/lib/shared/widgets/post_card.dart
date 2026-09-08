import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/models/enums.dart';
import '../../core/models/post.dart';
import '../../features/auth/auth_provider.dart';
import '../../features/comments/comment_section.dart';
import '../../features/moderation/report_dialog.dart';
import '../../features/posts/post_composer_dialog.dart';
import '../../features/posts/post_provider.dart';
import '../../features/posts/share_dialog.dart';
import '../../features/reactions/reaction_picker.dart';
import '../../features/reactions/reaction_provider.dart';
import '../../features/reels/reel_repository.dart';
import '../../features/saved/saved_provider.dart';
import 'post_content_text.dart';
import 'privacy_badge.dart';
import 'user_inline.dart';

/// Facebook-style post card: author header, content, media, tagged-users
/// line, reaction summary, and the like/comment/share action row. Also
/// renders a shared post as a nested mini-card, matching FB's repost UI.
class PostCard extends ConsumerStatefulWidget {
  final Post post;
  final bool initiallyShowComments;
  final bool openDetailOnTap;

  /// Called after this post is successfully deleted — `PostActions.delete`
  /// only knows how to remove the post from the home feed's own list; any
  /// OTHER list rendering this same post (a profile's posts, a group's,
  /// a fanpage's) has to be told separately, or it keeps showing/acting on
  /// a post that no longer exists on the backend. Pass a callback here that
  /// refreshes/removes from whatever list this card came from.
  final VoidCallback? onDeleted;

  /// Called after this post is successfully edited/pinned/unpinned — same
  /// reasoning as [onDeleted]: `PostActions.update/pin/unpin` only sync the
  /// home feed's own list, so any other list showing this post needs its
  /// own refresh to reflect the change without a manual reload.
  final VoidCallback? onUpdated;

  const PostCard({
    super.key,
    required this.post,
    this.initiallyShowComments = false,
    this.openDetailOnTap = true,
    this.onDeleted,
    this.onUpdated,
  });

  @override
  ConsumerState<PostCard> createState() => _PostCardState();
}

class _PostCardState extends ConsumerState<PostCard> {
  late bool _showComments = widget.initiallyShowComments;

  @override
  Widget build(BuildContext context) {
    final post = widget.post;
    final myId = ref.watch(currentAccountIdProvider);
    final isMine = myId != null && myId == post.authorId;

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: InkWell(
                    onTap: widget.openDetailOnTap
                        ? () => context.push('/post/${post.id}')
                        : null,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        UserInline(userId: post.authorId),
                        const SizedBox(height: 2),
                        Padding(
                          padding: const EdgeInsets.only(left: 40),
                          child: Row(
                            children: [
                              if (post.createdAt != null)
                                Text(
                                  DateFormat('dd/MM/yyyy HH:mm')
                                      .format(post.createdAt!),
                                  style: TextStyle(
                                      color: Colors.grey.shade600,
                                      fontSize: 12),
                                ),
                              const SizedBox(width: 6),
                              PrivacyBadge(privacy: post.privacy),
                              if (post.pinned) ...[
                                const SizedBox(width: 6),
                                Icon(Icons.push_pin,
                                    size: 12, color: Colors.grey.shade600),
                              ],
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                PopupMenuButton<String>(
                  onSelected: (value) => _onMenuSelected(context, value),
                  itemBuilder: (context) => isMine
                      ? [
                          const PopupMenuItem(value: 'edit', child: Text('Chỉnh sửa')),
                          PopupMenuItem(
                            value: post.pinned ? 'unpin' : 'pin',
                            child: Text(post.pinned ? 'Bỏ ghim' : 'Ghim bài viết'),
                          ),
                          const PopupMenuItem(value: 'delete', child: Text('Xoá')),
                        ]
                      : [
                          const PopupMenuItem(value: 'report', child: Text('Báo cáo bài viết')),
                        ],
                ),
              ],
            ),
            if (post.taggedUserIds.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(left: 40, top: 2),
                child: Wrap(
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text('cùng với ', style: TextStyle(color: Colors.grey.shade700)),
                    for (var i = 0; i < post.taggedUserIds.length; i++) ...[
                      UserNameText(
                        userId: post.taggedUserIds[i],
                        style: const TextStyle(fontWeight: FontWeight.bold),
                      ),
                      if (i != post.taggedUserIds.length - 1)
                        const Text(', '),
                    ],
                  ],
                ),
              ),
            if ((post.content ?? '').isNotEmpty) ...[
              const SizedBox(height: 10),
              PostContentText(content: post.content!, taggedUserIds: post.taggedUserIds),
            ],
            if (post.hasMedia) ...[
              const SizedBox(height: 10),
              _MediaGrid(urls: post.mediaUrls),
            ],
            if (post.sharedPostId != null) ...[
              const SizedBox(height: 10),
              _SharedPostPreview(sharedPostId: post.sharedPostId!),
            ] else if (post.sharedReelId != null) ...[
              const SizedBox(height: 10),
              _SharedReelPreview(sharedReelId: post.sharedReelId!),
            ],
            const SizedBox(height: 6),
            Row(
              children: [
                ReactionSummaryRow(
                  reactionKey: ReactionKey(
                    targetType: TargetType.post,
                    targetId: post.id,
                    targetOwnerId: post.authorId,
                  ),
                ),
                const Spacer(),
                if (post.commentCount > 0)
                  Text('${post.commentCount} bình luận',
                      style: TextStyle(color: Colors.grey.shade600, fontSize: 13)),
                if (post.shareCount > 0) ...[
                  const SizedBox(width: 8),
                  Text('${post.shareCount} lượt chia sẻ',
                      style: TextStyle(color: Colors.grey.shade600, fontSize: 13)),
                ],
              ],
            ),
            const Divider(height: 16),
            Row(
              children: [
                Expanded(
                  child: ReactionPicker(
                    reactionKey: ReactionKey(
                      targetType: TargetType.post,
                      targetId: post.id,
                      targetOwnerId: post.authorId,
                    ),
                  ),
                ),
                Expanded(
                  child: TextButton.icon(
                    onPressed: () =>
                        setState(() => _showComments = !_showComments),
                    icon: const Icon(Icons.mode_comment_outlined, size: 18),
                    label: const Text('Bình luận'),
                  ),
                ),
                Expanded(
                  child: TextButton.icon(
                    onPressed: () => showShareDialog(context, post),
                    icon: const Icon(Icons.share_outlined, size: 18),
                    label: const Text('Chia sẻ'),
                  ),
                ),
                Expanded(
                  child: _SaveButton(
                    saveKey: SaveKey(targetType: TargetType.post, targetId: post.id),
                    targetOwnerId: post.authorId,
                  ),
                ),
              ],
            ),
            if (_showComments) ...[
              const Divider(height: 16),
              CommentSection(targetId: post.id, targetOwnerId: post.authorId),
            ],
          ],
        ),
      ),
    );
  }

  void _onMenuSelected(BuildContext context, String value) async {
    final actions = ref.read(postActionsProvider);
    try {
      switch (value) {
        case 'edit':
          await showPostComposerDialog(context, editing: widget.post, onPosted: widget.onUpdated);
          break;
        case 'pin':
          await actions.pin(widget.post.id);
          widget.onUpdated?.call();
          break;
        case 'unpin':
          await actions.unpin(widget.post.id);
          widget.onUpdated?.call();
          break;
        case 'delete':
          final confirmed = await showDialog<bool>(
            context: context,
            // Use the dialog's OWN context for Navigator.pop, not the
            // outer PostCard's — if the feed reorders/rebuilds while this
            // confirmation is open (very possible: STOMP pushes, feed
            // refreshes, provider invalidations all happen independently
            // of a dialog being open), the outer context can end up
            // detached from the tree, and Navigator.of() on a detached
            // context finds no ancestor — which in release/profile builds
            // (where the framework's normal "no Navigator found" assertion
            // is compiled out) surfaces as a bare, unhelpful null-check
            // crash instead. The dialog's own builder context stays valid
            // as long as the dialog route itself is open, regardless of
            // what happens to the widget that launched it.
            builder: (dialogContext) => AlertDialog(
              title: const Text('Xoá bài viết?'),
              content: const Text('Hành động này không thể hoàn tác.'),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(dialogContext, false),
                    child: const Text('Huỷ')),
                TextButton(
                    onPressed: () => Navigator.pop(dialogContext, true),
                    child: const Text('Xoá')),
              ],
            ),
          );
          if (confirmed == true) {
            await actions.delete(widget.post.id);
            widget.onDeleted?.call();
          }
          break;
        case 'report':
          await showReportDialog(context, targetType: ReportTargetType.post, targetId: widget.post.id);
          break;
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Thao tác thất bại: $e')),
      );
    }
  }
}

class _MediaGrid extends StatelessWidget {
  final List<String> urls;

  const _MediaGrid({required this.urls});

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: GridView.builder(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: urls.length,
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: urls.length == 1 ? 1 : 2,
          mainAxisSpacing: 4,
          crossAxisSpacing: 4,
          childAspectRatio: urls.length == 1 ? 16 / 10 : 1,
        ),
        itemBuilder: (context, index) => CachedNetworkImage(
          imageUrl: urls[index],
          fit: BoxFit.cover,
          placeholder: (context, _) => Container(color: Colors.grey.shade200),
          errorWidget: (context, _, __) =>
              Container(color: Colors.grey.shade200, child: const Icon(Icons.broken_image)),
        ),
      ),
    );
  }
}

class _SharedPostPreview extends ConsumerWidget {
  final String sharedPostId;

  const _SharedPostPreview({required this.sharedPostId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncPost = ref.watch(_sharedPostProvider(sharedPostId));

    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        border: Border.all(color: const Color(0xFFDADDE1)),
        borderRadius: BorderRadius.circular(8),
      ),
      child: asyncPost.when(
        data: (post) {
          if (post == null) {
            return const Text('Bài viết gốc không còn khả dụng',
                style: TextStyle(color: Colors.grey));
          }
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              UserInline(userId: post.authorId, avatarRadius: 14),
              if ((post.content ?? '').isNotEmpty) ...[
                const SizedBox(height: 6),
                PostContentText(content: post.content!, taggedUserIds: post.taggedUserIds),
              ],
              if (post.hasMedia) ...[
                const SizedBox(height: 6),
                _MediaGrid(urls: post.mediaUrls),
              ],
            ],
          );
        },
        loading: () => const SizedBox(
            height: 40,
            child: Center(
                child: SizedBox(
                    height: 16, width: 16, child: CircularProgressIndicator(strokeWidth: 2)))),
        error: (_, __) => const Text('Không tải được bài viết gốc',
            style: TextStyle(color: Colors.grey)),
      ),
    );
  }
}

final _sharedPostProvider = FutureProvider.family((ref, String id) async {
  try {
    return await ref.read(postRepositoryProvider).getById(id);
  } catch (_) {
    return null;
  }
});

class _SharedReelPreview extends ConsumerWidget {
  final String sharedReelId;

  const _SharedReelPreview({required this.sharedReelId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncReel = ref.watch(_sharedReelProvider(sharedReelId));

    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        border: Border.all(color: const Color(0xFFDADDE1)),
        borderRadius: BorderRadius.circular(8),
      ),
      child: asyncReel.when(
        data: (reel) {
          if (reel == null) {
            return const Text('Reel gốc không còn khả dụng', style: TextStyle(color: Colors.grey));
          }
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              UserInline(userId: reel.authorId, avatarRadius: 14),
              const SizedBox(height: 6),
              InkWell(
                onTap: () => context.push('/reels'),
                borderRadius: BorderRadius.circular(8),
                child: Container(
                  height: 160,
                  width: double.infinity,
                  decoration: BoxDecoration(color: Colors.black87, borderRadius: BorderRadius.circular(8)),
                  child: const Center(child: Icon(Icons.play_circle_fill, color: Colors.white, size: 40)),
                ),
              ),
              if (reel.caption != null && reel.caption!.isNotEmpty) ...[
                const SizedBox(height: 6),
                Text(reel.caption!),
              ],
            ],
          );
        },
        loading: () => const SizedBox(
            height: 40,
            child: Center(
                child: SizedBox(
                    height: 16, width: 16, child: CircularProgressIndicator(strokeWidth: 2)))),
        error: (_, __) => const Text('Không tải được reel gốc', style: TextStyle(color: Colors.grey)),
      ),
    );
  }
}

final _sharedReelProvider = FutureProvider.family((ref, String id) async {
  try {
    return await ref.read(reelRepositoryProvider).getById(id);
  } catch (_) {
    return null;
  }
});

/// Facebook-style "Lưu" (save/bookmark) button — shared by posts
/// (post_card.dart) and reels (reels_feed_page.dart), backed by the generic
/// SavedItem feature in reaction-service (see saved_provider.dart).
class _SaveButton extends ConsumerWidget {
  final SaveKey saveKey;
  final String targetOwnerId;

  const _SaveButton({required this.saveKey, required this.targetOwnerId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final saved = ref.watch(savedProvider(saveKey));
    return TextButton.icon(
      onPressed: () => ref.read(savedProvider(saveKey).notifier).toggle(targetOwnerId: targetOwnerId),
      icon: Icon(saved ? Icons.bookmark : Icons.bookmark_border, size: 18),
      label: Text(saved ? 'Đã lưu' : 'Lưu'),
    );
  }
}
