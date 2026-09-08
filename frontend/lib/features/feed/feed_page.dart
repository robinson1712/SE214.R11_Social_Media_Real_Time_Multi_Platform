import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../shared/widgets/post_card.dart';
import '../auth/current_user_widgets.dart';
import '../posts/post_composer_dialog.dart';
import '../stories/stories_strip.dart';
import 'feed_provider.dart';

class FeedPage extends ConsumerStatefulWidget {
  const FeedPage({super.key});

  @override
  ConsumerState<FeedPage> createState() => _FeedPageState();
}

class _FeedPageState extends ConsumerState<FeedPage> {
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(() {
      if (_scrollController.position.pixels >
          _scrollController.position.maxScrollExtent - 400) {
        ref.read(feedProvider.notifier).loadMore();
      }
    });
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(feedProvider);

    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 680),
        child: RefreshIndicator(
          onRefresh: () => ref.read(feedProvider.notifier).refresh(),
          child: ListView(
            controller: _scrollController,
            padding: const EdgeInsets.all(12),
            children: [
              const StoriesStrip(),
              const SizedBox(height: 12),
              _ComposerBox(),
              const SizedBox(height: 12),
              if (state.loading)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 40),
                  child: Center(child: CircularProgressIndicator()),
                )
              else if (state.posts.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 40),
                  child: Center(
                    child: Text('Chưa có bài viết nào trong bảng tin của bạn',
                        style: TextStyle(color: Colors.grey.shade600)),
                  ),
                )
              else
                ...state.posts.map((post) => PostCard(key: ValueKey(post.id), post: post)),
              if (state.loadingMore)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 16),
                  child: Center(
                      child: SizedBox(
                          height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ComposerBox extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
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
                onTap: () => showPostComposerDialog(context),
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF0F2F5),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text('Bạn đang nghĩ gì?',
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
