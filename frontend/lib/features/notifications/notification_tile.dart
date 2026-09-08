import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/app_notification.dart';
import '../../core/models/enums.dart';
import '../../shared/utils/relative_time.dart';
import '../../shared/widgets/avatar.dart';
import '../fanpages/fanpage_repository.dart';
import '../groups/group_repository.dart';
import '../posts/post_provider.dart';
import '../profile/user_lookup_provider.dart';
import 'notification_navigation.dart';
import 'notification_provider.dart';

/// The group/page a POST-target notification's post belongs to, if any —
/// resolved by fetching the post then its group/page, so a comment/reaction/
/// tag notification can show "... trong <Tên nhóm>" like Facebook does.
final _postContextNameProvider =
    FutureProvider.autoDispose.family<String?, String>((ref, postId) async {
  try {
    final post = await ref.read(postRepositoryProvider).getById(postId);
    if (post.groupId != null) {
      final group = await ref.read(groupRepositoryProvider).getById(post.groupId!);
      return group.name;
    }
    if (post.pageId != null) {
      final page = await ref.read(fanpageRepositoryProvider).getById(post.pageId!);
      return page.name;
    }
  } catch (_) {
    // Post/group/page may since have been deleted — just omit the context.
  }
  return null;
});

String _actionPhrase(AppNotification n) {
  switch (n.type) {
    case NotificationType.friendRequest:
      return n.message.toLowerCase().contains('accepted')
          ? 'đã chấp nhận lời mời kết bạn của bạn'
          : 'đã gửi cho bạn lời mời kết bạn';
    case NotificationType.comment:
      return 'đã bình luận về bài viết của bạn';
    case NotificationType.reaction:
      final target = n.targetType == 'REEL'
          ? 'thước phim'
          : n.targetType == 'STORY'
              ? 'tin'
              : 'bài viết';
      return 'đã bày tỏ cảm xúc về $target của bạn';
    case NotificationType.group:
      return 'đã cập nhật nhóm của bạn';
    case NotificationType.match:
      return 'đã ghép đôi với bạn';
    case NotificationType.message:
      return 'đã nhắn tin cho bạn';
    case NotificationType.tag:
      return 'đã gắn thẻ bạn trong một bài viết';
    case NotificationType.follow:
      return 'đã theo dõi bạn';
  }
}

/// Small colored badge (Facebook-style) overlaid on the actor's avatar,
/// signaling the notification type at a glance.
class _TypeBadge extends StatelessWidget {
  final NotificationType type;

  const _TypeBadge({required this.type});

  Color get _color {
    switch (type) {
      case NotificationType.friendRequest:
      case NotificationType.follow:
        return const Color(0xFF1877F2);
      case NotificationType.reaction:
        return const Color(0xFFF7B928);
      case NotificationType.comment:
      case NotificationType.tag:
        return const Color(0xFF42B72A);
      case NotificationType.group:
        return const Color(0xFF1877F2);
      case NotificationType.match:
        return const Color(0xFFE91E63);
      case NotificationType.message:
        return const Color(0xFF1877F2);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 20,
      height: 20,
      decoration: BoxDecoration(
        color: _color,
        shape: BoxShape.circle,
        border: Border.all(color: Colors.white, width: 2),
      ),
      alignment: Alignment.center,
      child: Text(type.icon, style: const TextStyle(fontSize: 10)),
    );
  }
}

/// One Facebook-style notification row: actor avatar (with a type badge),
/// "Actor đã <hành động> [trong <Nhóm/Trang>]", relative time, an unread
/// dot, and a close button to delete it. The bell dropdown is the sole
/// place notifications are browsed (paginated via scroll), so this is its
/// only consumer.
class NotificationTile extends ConsumerWidget {
  final AppNotification notification;
  final VoidCallback? onAfterTap;

  const NotificationTile({super.key, required this.notification, this.onAfterTap});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final n = notification;
    final actor = ref.watch(userLookupProvider(n.actorId)).value;
    final actorName = actor?.fullName ?? 'Người dùng';
    final contextName = n.targetType == 'POST' && n.targetId != null
        ? ref.watch(_postContextNameProvider(n.targetId!)).value
        : null;

    return InkWell(
      onTap: () {
        if (!n.read) ref.read(notificationProvider.notifier).markRead(n.id);
        navigateForNotification(context, n);
        onAfterTap?.call();
      },
      child: Container(
        color: n.read ? null : const Color(0xFFE7F0FF),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Stack(
              clipBehavior: Clip.none,
              children: [
                Avatar(url: actor?.avatarUrl, name: actorName, radius: 22, gender: actor?.gender),
                Positioned(right: -2, bottom: -2, child: _TypeBadge(type: n.type)),
              ],
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  RichText(
                    text: TextSpan(
                      style: DefaultTextStyle.of(context).style.copyWith(fontSize: 13.5, height: 1.3),
                      children: [
                        TextSpan(
                          text: actorName,
                          style: TextStyle(fontWeight: n.read ? FontWeight.w600 : FontWeight.bold),
                        ),
                        TextSpan(text: ' ${_actionPhrase(n)}'),
                        if (contextName != null) ...[
                          const TextSpan(text: ' trong '),
                          TextSpan(
                            text: contextName,
                            style: const TextStyle(fontWeight: FontWeight.bold),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    relativeTime(n.createdAt ?? DateTime.now()),
                    style: TextStyle(
                      fontSize: 12,
                      color: n.read ? Colors.grey.shade600 : Theme.of(context).primaryColor,
                      fontWeight: n.read ? FontWeight.normal : FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),
            if (!n.read)
              Container(
                margin: const EdgeInsets.only(top: 6, left: 4),
                width: 10,
                height: 10,
                decoration: BoxDecoration(color: Theme.of(context).primaryColor, shape: BoxShape.circle),
              ),
            IconButton(
              icon: Icon(Icons.close, size: 16, color: Colors.grey.shade600),
              tooltip: 'Xoá thông báo',
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
              onPressed: () => ref.read(notificationProvider.notifier).delete(n.id),
            ),
          ],
        ),
      ),
    );
  }
}
