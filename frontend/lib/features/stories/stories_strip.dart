import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/story.dart';
import '../auth/auth_provider.dart';
import '../profile/user_lookup_provider.dart';
import 'create_story_dialog.dart';
import 'story_provider.dart';
import 'story_viewer.dart';

/// Horizontal strip of story tiles at the top of the feed, Facebook-style:
/// a "create" tile first, then one tile per author with an unviewed/viewed
/// ring around their avatar.
class StoriesStrip extends ConsumerWidget {
  const StoriesStrip({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final groupsAsync = ref.watch(groupedStoryFeedProvider);
    final myId = ref.watch(currentAccountIdProvider);

    return SizedBox(
      height: 120,
      child: groupsAsync.when(
        data: (groups) => ListView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 4),
          children: [
            _CreateStoryTile(),
            ...groups.map((stories) => _StoryTile(
                  authorId: stories.first.authorId,
                  stories: stories,
                  isMine: stories.first.authorId == myId,
                )),
          ],
        ),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => const SizedBox.shrink(),
      ),
    );
  }
}

class _CreateStoryTile extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => showCreateStoryDialog(context),
      child: Container(
        width: 84,
        margin: const EdgeInsets.symmetric(horizontal: 6, vertical: 8),
        decoration: BoxDecoration(
          color: const Color(0xFFF0F2F5),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFFDADDE1)),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircleAvatar(
              radius: 18,
              backgroundColor: Theme.of(context).primaryColor,
              child: const Icon(Icons.add, color: Colors.white),
            ),
            const SizedBox(height: 6),
            const Text('Tạo story', style: TextStyle(fontSize: 11), textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}

class _StoryTile extends ConsumerWidget {
  final String authorId;
  final List<Story> stories;
  final bool isMine;

  const _StoryTile({required this.authorId, required this.stories, required this.isMine});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(userLookupProvider(authorId)).value;

    return GestureDetector(
      onTap: () => showStoryViewer(context, stories, 0),
      child: Container(
        width: 84,
        margin: const EdgeInsets.symmetric(horizontal: 6, vertical: 8),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: Theme.of(context).primaryColor, width: 2),
        ),
        clipBehavior: Clip.antiAlias,
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (profile?.avatarUrl != null)
              Image.network(profile!.avatarUrl!, fit: BoxFit.cover)
            else
              Container(
                color: Theme.of(context).primaryColor,
                alignment: Alignment.center,
                child: Text(
                  (profile?.fullName.isNotEmpty ?? false) ? profile!.fullName[0].toUpperCase() : '?',
                  style: const TextStyle(color: Colors.white, fontSize: 28, fontWeight: FontWeight.bold),
                ),
              ),
            Positioned(
              bottom: 4,
              left: 4,
              right: 4,
              child: Text(
                isMine ? 'Story của bạn' : (profile?.fullName ?? ''),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 10,
                  color: Colors.white,
                  fontWeight: FontWeight.bold,
                  shadows: [Shadow(blurRadius: 3, color: Colors.black)],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
