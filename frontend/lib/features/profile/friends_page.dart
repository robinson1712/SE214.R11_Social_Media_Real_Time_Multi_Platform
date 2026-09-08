import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/models/friendship.dart';
import '../../core/models/page_response.dart';
import '../../core/models/user_profile.dart';
import '../../shared/widgets/avatar.dart';
import '../../shared/widgets/user_inline.dart';
import 'profile_provider.dart';
import 'profile_repository.dart';

final _myFriendsProvider = FutureProvider<PageResponse<UserProfile>>((ref) {
  return ref.read(profileRepositoryProvider).myFriends(size: 100);
});

class FriendsPage extends ConsumerWidget {
  const FriendsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final friendsAsync = ref.watch(_myFriendsProvider);
    final requestsAsync = ref.watch(pendingFriendRequestsProvider);

    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 560),
        child: ListView(
            padding: const EdgeInsets.all(12),
            children: [
              requestsAsync.when(
                data: (requests) => requests.isEmpty
                    ? const SizedBox.shrink()
                    : _FriendRequestsSection(requests: requests),
                loading: () => const SizedBox.shrink(),
                error: (_, __) => const SizedBox.shrink(),
              ),
              Text('Bạn bè', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              friendsAsync.when(
                data: (page) {
                  if (page.content.isEmpty) {
                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 24),
                      child: Text('Bạn chưa có bạn bè nào',
                          style: TextStyle(color: Colors.grey.shade600)),
                    );
                  }
                  return Column(
                    children: page.content
                        .map((f) => Card(
                              margin: const EdgeInsets.only(bottom: 8),
                              child: ListTile(
                                leading: Avatar(url: f.avatarUrl, name: f.fullName, gender: f.gender),
                                title: Text(f.fullName),
                                subtitle: f.location != null ? Text(f.location!) : null,
                                onTap: () => context.push('/profile/${f.id}'),
                              ),
                            ))
                        .toList(),
                  );
                },
                loading: () => const Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: Center(child: CircularProgressIndicator()),
                ),
                error: (e, _) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 24),
                  child: Text('Không tải được danh sách bạn bè: $e'),
                ),
              ),
            ],
          ),
      ),
    );
  }
}

class _FriendRequestsSection extends ConsumerWidget {
  final List<Friendship> requests;

  const _FriendRequestsSection({required this.requests});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Lời mời kết bạn (${requests.length})',
            style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        ...requests.map((r) => Card(
              margin: const EdgeInsets.only(bottom: 8),
              child: ListTile(
                title: UserInline(userId: r.requesterId),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    ElevatedButton(
                      onPressed: () async {
                        await ref.read(friendshipActionsProvider).accept(r.id);
                        ref.invalidate(_myFriendsProvider);
                      },
                      child: const Text('Chấp nhận'),
                    ),
                    const SizedBox(width: 8),
                    OutlinedButton(
                      onPressed: () => ref.read(friendshipActionsProvider).decline(r.id),
                      child: const Text('Từ chối'),
                    ),
                  ],
                ),
              ),
            )),
        const Divider(height: 24),
      ],
    );
  }
}
