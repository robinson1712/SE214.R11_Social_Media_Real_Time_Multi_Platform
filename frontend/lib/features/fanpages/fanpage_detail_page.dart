import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../shared/widgets/avatar.dart';
import '../../shared/widgets/post_card.dart';
import '../auth/auth_provider.dart';
import '../posts/post_composer_dialog.dart';
import '../posts/post_provider.dart';
import 'fanpage_provider.dart';

class FanpageDetailPage extends ConsumerWidget {
  final String pageId;

  const FanpageDetailPage({super.key, required this.pageId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncPage = ref.watch(fanpageDetailProvider(pageId));
    final myId = ref.watch(currentAccountIdProvider);
    final followersAsync = ref.watch(fanpageFollowersProvider(pageId));

    // No page-level AppBar — the page's own name is already shown
    // prominently in its header card below, and AppShell owns the app's
    // one persistent top bar.
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 680),
        child: asyncPage.when(
            data: (page) {
              final isFollowing = followersAsync.value?.content
                      .any((f) => f.userId == myId) ??
                  false;
              return ListView(
                padding: const EdgeInsets.all(12),
                children: [
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        children: [
                          if (page.coverUrl != null)
                            ClipRRect(
                              borderRadius: BorderRadius.circular(8),
                              child: Image.network(page.coverUrl!,
                                  height: 160, width: double.infinity, fit: BoxFit.cover),
                            ),
                          const SizedBox(height: 12),
                          Avatar(url: page.avatarUrl, name: page.name, radius: 44),
                          const SizedBox(height: 8),
                          Text(page.name, style: Theme.of(context).textTheme.headlineSmall),
                          if (page.category != null)
                            Text(page.category!, style: TextStyle(color: Colors.grey.shade600)),
                          if (page.description != null && page.description!.isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: 4),
                              child: Text(page.description!, textAlign: TextAlign.center),
                            ),
                          const SizedBox(height: 4),
                          Text('${page.followerCount} người theo dõi',
                              style: TextStyle(color: Colors.grey.shade600)),
                          const SizedBox(height: 12),
                          isFollowing
                              ? OutlinedButton.icon(
                                  icon: const Icon(Icons.check),
                                  label: const Text('Đang theo dõi'),
                                  onPressed: () => ref.read(fanpageActionsProvider).unfollow(pageId),
                                )
                              : ElevatedButton.icon(
                                  icon: const Icon(Icons.add),
                                  label: const Text('Theo dõi'),
                                  onPressed: () => ref.read(fanpageActionsProvider).follow(pageId),
                                ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Text('Bài viết', style: Theme.of(context).textTheme.titleMedium),
                      const Spacer(),
                      Consumer(builder: (context, ref, _) {
                        final managed = ref.watch(myManagedFanpagesProvider).value?.content ?? const [];
                        final isManaging = managed.any((p) => p.id == pageId);
                        if (!isManaging) return const SizedBox.shrink();
                        return TextButton.icon(
                          icon: const Icon(Icons.add),
                          label: const Text('Đăng bài'),
                          onPressed: () async {
                            await showPostComposerDialog(context, pageId: pageId);
                            ref.invalidate(_pagePostsProvider(pageId));
                          },
                        );
                      }),
                    ],
                  ),
                  const SizedBox(height: 8),
                  _PagePosts(pageId: pageId),
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

class _PagePosts extends ConsumerWidget {
  final String pageId;

  const _PagePosts({required this.pageId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncPosts = ref.watch(_pagePostsProvider(pageId));
    return asyncPosts.when(
      data: (page) => page.content.isEmpty
          ? Padding(
              padding: const EdgeInsets.symmetric(vertical: 20),
              child: Text('Chưa có bài viết nào', style: TextStyle(color: Colors.grey.shade600)),
            )
          : Column(
              children: page.content
                  .map((p) => PostCard(
                        key: ValueKey(p.id),
                        post: p,
                        onDeleted: () => ref.invalidate(_pagePostsProvider(pageId)),
                        onUpdated: () => ref.invalidate(_pagePostsProvider(pageId)),
                      ))
                  .toList(),
            ),
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 20),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (e, _) => Text('Lỗi: $e'),
    );
  }
}

final _pagePostsProvider = FutureProvider.family((ref, String pageId) async {
  return ref.read(postRepositoryProvider).byPage(pageId);
});
