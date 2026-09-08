import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../shared/widgets/post_card.dart';
import 'post_provider.dart';

final _postDetailProvider =
    FutureProvider.family((ref, String postId) async {
  return ref.read(postRepositoryProvider).getById(postId);
});

class PostDetailPage extends ConsumerWidget {
  final String postId;

  const PostDetailPage({super.key, required this.postId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncPost = ref.watch(_postDetailProvider(postId));

    // No page-level AppBar — AppShell already owns the app's one persistent
    // top bar.
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 620),
        child: asyncPost.when(
          data: (post) => SingleChildScrollView(
            padding: const EdgeInsets.all(12),
            child: PostCard(
              post: post,
              initiallyShowComments: true,
              openDetailOnTap: false,
              onUpdated: () => ref.invalidate(_postDetailProvider(postId)),
              onDeleted: () {
                // The post this whole page is about no longer exists —
                // nothing left to render here, so back out rather than
                // stay on a page whose only content just vanished.
                if (context.canPop()) {
                  context.pop();
                } else {
                  context.go('/');
                }
              },
            ),
          ),
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Padding(
            padding: const EdgeInsets.all(24),
            child: Text('Không tải được bài viết: $e'),
          ),
        ),
      ),
    );
  }
}
