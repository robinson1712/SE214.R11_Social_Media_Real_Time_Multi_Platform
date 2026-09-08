import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/enums.dart';
import '../../shared/widgets/avatar.dart';
import '../../shared/widgets/post_card.dart';
import '../../shared/widgets/user_inline.dart';
import '../auth/auth_provider.dart';
import '../posts/post_composer_dialog.dart';
import '../posts/post_provider.dart';
import 'group_provider.dart';

final _groupPostsProvider = FutureProvider.family((ref, String groupId) async {
  return ref.read(postRepositoryProvider).byGroup(groupId);
});

class GroupDetailPage extends ConsumerWidget {
  final String groupId;

  const GroupDetailPage({super.key, required this.groupId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncGroup = ref.watch(groupDetailProvider(groupId));
    final myId = ref.watch(currentAccountIdProvider);

    // No page-level AppBar — the group's own name is already shown
    // prominently in its header card below, and AppShell owns the app's
    // one persistent top bar.
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 640),
        child: asyncGroup.when(
            data: (group) {
              final membersAsync = ref.watch(groupMembersProvider(groupId));
              final myMembership = membersAsync.value?.content
                  .where((m) => m.userId == myId)
                  .toList();
              final myRole = (myMembership != null && myMembership.isNotEmpty)
                  ? myMembership.first.role
                  : null;
              final isApprovedMember = (myMembership != null && myMembership.isNotEmpty) &&
                  myMembership.first.status == MemberStatus.approved;
              final isAdmin = myRole == MemberRole.admin;

              return ListView(
                padding: const EdgeInsets.all(12),
                children: [
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        children: [
                          Avatar(url: group.avatarUrl, name: group.name, radius: 44),
                          const SizedBox(height: 8),
                          Text(group.name, style: Theme.of(context).textTheme.headlineSmall),
                          if (group.description != null && group.description!.isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: 4),
                              child: Text(group.description!, textAlign: TextAlign.center),
                            ),
                          const SizedBox(height: 4),
                          Text('${group.memberCount} thành viên · ${group.privacy.label}',
                              style: TextStyle(color: Colors.grey.shade600)),
                          const SizedBox(height: 12),
                          if (isApprovedMember)
                            OutlinedButton.icon(
                              icon: const Icon(Icons.exit_to_app),
                              label: const Text('Rời nhóm'),
                              onPressed: () => ref.read(groupActionsProvider).leave(groupId),
                            )
                          else
                            ElevatedButton.icon(
                              icon: const Icon(Icons.group_add),
                              label: const Text('Tham gia nhóm'),
                              onPressed: () => ref.read(groupActionsProvider).join(groupId),
                            ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text('Thành viên', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  membersAsync.when(
                    data: (page) => Column(
                      children: page.content.map((m) {
                        final pending = m.status == MemberStatus.pending;
                        return Card(
                          margin: const EdgeInsets.only(bottom: 6),
                          child: ListTile(
                            title: UserInline(userId: m.userId),
                            subtitle: Text(pending ? 'Chờ duyệt' : m.role.label),
                            trailing: !isAdmin
                                ? null
                                : PopupMenuButton<String>(
                                    onSelected: (value) {
                                      final actions = ref.read(groupActionsProvider);
                                      switch (value) {
                                        case 'approve':
                                          actions.approve(groupId, m.userId);
                                          break;
                                        case 'kick':
                                          actions.kick(groupId, m.userId);
                                          break;
                                        case 'make_admin':
                                          actions.changeRole(groupId, m.userId, MemberRole.admin);
                                          break;
                                        case 'make_mod':
                                          actions.changeRole(groupId, m.userId, MemberRole.moderator);
                                          break;
                                        case 'make_member':
                                          actions.changeRole(groupId, m.userId, MemberRole.member);
                                          break;
                                      }
                                    },
                                    itemBuilder: (context) => [
                                      if (pending)
                                        const PopupMenuItem(value: 'approve', child: Text('Duyệt')),
                                      if (!pending) ...[
                                        const PopupMenuItem(value: 'make_admin', child: Text('Đặt làm quản trị viên')),
                                        const PopupMenuItem(value: 'make_mod', child: Text('Đặt làm kiểm duyệt viên')),
                                        const PopupMenuItem(value: 'make_member', child: Text('Đặt làm thành viên')),
                                      ],
                                      const PopupMenuItem(value: 'kick', child: Text('Xoá khỏi nhóm')),
                                    ],
                                  ),
                          ),
                        );
                      }).toList(),
                    ),
                    loading: () => const Center(child: CircularProgressIndicator()),
                    error: (e, _) => Text('Lỗi: $e'),
                  ),
                  if (isApprovedMember) ...[
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Text('Bài viết', style: Theme.of(context).textTheme.titleMedium),
                        const Spacer(),
                        TextButton.icon(
                          icon: const Icon(Icons.add),
                          label: const Text('Đăng bài trong nhóm'),
                          onPressed: () async {
                            await showPostComposerDialog(context, groupId: groupId);
                            ref.invalidate(_groupPostsProvider(groupId));
                          },
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Consumer(
                      builder: (context, ref, _) {
                        final asyncPosts = ref.watch(_groupPostsProvider(groupId));
                        return asyncPosts.when(
                          data: (page) => page.content.isEmpty
                              ? Padding(
                                  padding: const EdgeInsets.symmetric(vertical: 20),
                                  child: Text('Chưa có bài viết nào',
                                      style: TextStyle(color: Colors.grey.shade600)),
                                )
                              : Column(
                                  children: page.content
                                      .map((p) => PostCard(
                                            key: ValueKey(p.id),
                                            post: p,
                                            onDeleted: () => ref.invalidate(_groupPostsProvider(groupId)),
                                            onUpdated: () => ref.invalidate(_groupPostsProvider(groupId)),
                                          ))
                                      .toList(),
                                ),
                          loading: () => const Center(child: CircularProgressIndicator()),
                          error: (e, _) => Text('Lỗi: $e'),
                        );
                      },
                    ),
                  ],
                ],
              );
            },
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Text('Lỗi: $e'),
        ),
      ),
    );
  }
}
