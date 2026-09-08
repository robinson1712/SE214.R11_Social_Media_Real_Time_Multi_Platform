import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/models/enums.dart';
import '../../core/models/saved_item.dart';
import '../../shared/widgets/post_card.dart';
import '../../shared/widgets/user_inline.dart';
import '../posts/post_provider.dart';
import '../reels/reel_repository.dart';
import 'saved_provider.dart';

/// "Đã lưu" — everything the current user has bookmarked, posts and reels
/// mixed together newest-first (matching what `GET /api/saved/mine`
/// actually returns), each row fetched by id/type the same way PostCard's
/// shared-post/shared-reel previews already do.
class SavedPage extends ConsumerWidget {
  const SavedPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncSaved = ref.watch(mySavedItemsProvider);

    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 680),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
              child: Text('Đã lưu', style: Theme.of(context).textTheme.titleLarge),
            ),
            Expanded(
              child: asyncSaved.when(
                data: (page) => page.content.isEmpty
                    ? Padding(
                        padding: const EdgeInsets.symmetric(vertical: 40),
                        child: Center(
                          child: Text('Bạn chưa lưu bài viết hoặc reel nào',
                              style: TextStyle(color: Colors.grey.shade600)),
                        ),
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        itemCount: page.content.length,
                        itemBuilder: (context, index) {
                          final item = page.content[index];
                          return switch (item.targetType) {
                            TargetType.reel => _SavedReelTile(item: item),
                            _ => _SavedPostTile(item: item),
                          };
                        },
                      ),
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (e, _) => Center(child: Text('Lỗi: $e')),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SavedPostTile extends ConsumerWidget {
  final SavedItem item;

  const _SavedPostTile({required this.item});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncPost = ref.watch(_savedPostProvider(item.targetId));
    return asyncPost.when(
      data: (post) => post == null
          ? const SizedBox.shrink()
          : PostCard(key: ValueKey(post.id), post: post),
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 12),
        child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
      ),
      error: (_, __) => const SizedBox.shrink(),
    );
  }
}

final _savedPostProvider = FutureProvider.family((ref, String id) async {
  try {
    return await ref.read(postRepositoryProvider).getById(id);
  } catch (_) {
    return null;
  }
});

class _SavedReelTile extends ConsumerWidget {
  final SavedItem item;

  const _SavedReelTile({required this.item});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncReel = ref.watch(_savedReelProvider(item.targetId));
    return asyncReel.when(
      data: (reel) {
        if (reel == null) return const SizedBox.shrink();
        return Card(
          margin: const EdgeInsets.only(bottom: 8),
          child: ListTile(
            leading: InkWell(
              onTap: () => context.push('/reels'),
              child: Container(
                width: 56,
                height: 56,
                decoration: BoxDecoration(color: Colors.black87, borderRadius: BorderRadius.circular(6)),
                child: const Icon(Icons.play_circle_fill, color: Colors.white),
              ),
            ),
            title: UserInline(userId: reel.authorId, avatarRadius: 12),
            subtitle: reel.caption != null && reel.caption!.isNotEmpty
                ? Text(reel.caption!, maxLines: 1, overflow: TextOverflow.ellipsis)
                : null,
            onTap: () => context.push('/reels'),
          ),
        );
      },
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 12),
        child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
      ),
      error: (_, __) => const SizedBox.shrink(),
    );
  }
}

final _savedReelProvider = FutureProvider.family((ref, String id) async {
  try {
    return await ref.read(reelRepositoryProvider).getById(id);
  } catch (_) {
    return null;
  }
});
