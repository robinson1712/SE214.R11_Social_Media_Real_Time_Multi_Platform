import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/post.dart';
import '../feed/feed_provider.dart';
import 'post_repository.dart';

final postRepositoryProvider = Provider((ref) => PostRepository());

/// Thin action layer over [PostRepository] that also keeps the feed's
/// in-memory list in sync, so the UI reflects create/update/delete/pin/share
/// immediately without a full feed refetch.
class PostActions {
  final Ref _ref;

  PostActions(this._ref);

  PostRepository get _repository => _ref.read(postRepositoryProvider);

  Future<Post> create(CreatePostRequest request) async {
    final post = await _repository.create(request);
    _ref.read(feedProvider.notifier).prepend(post);
    return post;
  }

  Future<Post> update(String id, UpdatePostRequest request) async {
    final post = await _repository.update(id, request);
    _ref.read(feedProvider.notifier).replace(post);
    return post;
  }

  Future<void> delete(String id) async {
    await _repository.delete(id);
    _ref.read(feedProvider.notifier).remove(id);
  }

  Future<Post> pin(String id) async {
    final post = await _repository.pin(id);
    _ref.read(feedProvider.notifier).replace(post);
    return post;
  }

  Future<Post> unpin(String id) async {
    final post = await _repository.unpin(id);
    _ref.read(feedProvider.notifier).replace(post);
    return post;
  }

  Future<Post> share(String id, ShareRequest request) async {
    final post = await _repository.share(id, request);
    _ref.read(feedProvider.notifier).prepend(post);
    return post;
  }

  Future<Post> shareReel(String reelId, ShareRequest request) async {
    final post = await _repository.shareReel(reelId, request);
    _ref.read(feedProvider.notifier).prepend(post);
    return post;
  }
}

final postActionsProvider = Provider((ref) => PostActions(ref));
