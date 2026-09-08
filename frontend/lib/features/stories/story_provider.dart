import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/story.dart';
import 'story_repository.dart';

final storyFeedProvider = FutureProvider<List<Story>>((ref) {
  return ref.read(storyRepositoryProvider).feed();
});

/// Stories grouped by author, most-recent story first within each group,
/// groups ordered by their most recent story — matches the Facebook strip
/// UX of "one tile per person, tap to see all of theirs."
final groupedStoryFeedProvider = FutureProvider<List<List<Story>>>((ref) async {
  final stories = await ref.watch(storyFeedProvider.future);
  final byAuthor = <String, List<Story>>{};
  for (final s in stories) {
    (byAuthor[s.authorId] ??= []).add(s);
  }
  final groups = byAuthor.values.toList()
    ..sort((a, b) {
      final aTime = a.first.createdAt ?? DateTime(0);
      final bTime = b.first.createdAt ?? DateTime(0);
      return bTime.compareTo(aTime);
    });
  return groups;
});

class StoryActions {
  final Ref _ref;

  StoryActions(this._ref);

  StoryRepository get _repository => _ref.read(storyRepositoryProvider);

  Future<Story> create(CreateStoryRequest request) async {
    final story = await _repository.create(request);
    _ref.invalidate(storyFeedProvider);
    return story;
  }

  Future<void> view(String id) async {
    await _repository.view(id);
  }

  Future<void> delete(String id) async {
    await _repository.delete(id);
    _ref.invalidate(storyFeedProvider);
  }
}

final storyActionsProvider = Provider((ref) => StoryActions(ref));
