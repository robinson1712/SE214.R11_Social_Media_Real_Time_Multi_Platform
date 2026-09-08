import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/post.dart';
import 'feed_repository.dart';

class FeedState {
  final List<Post> posts;
  final bool loading;
  final bool loadingMore;
  final bool hasMore;
  final int page;
  final String? error;

  const FeedState({
    this.posts = const [],
    this.loading = false,
    this.loadingMore = false,
    this.hasMore = true,
    this.page = 0,
    this.error,
  });

  FeedState copyWith({
    List<Post>? posts,
    bool? loading,
    bool? loadingMore,
    bool? hasMore,
    int? page,
    String? error,
    bool clearError = false,
  }) =>
      FeedState(
        posts: posts ?? this.posts,
        loading: loading ?? this.loading,
        loadingMore: loadingMore ?? this.loadingMore,
        hasMore: hasMore ?? this.hasMore,
        page: page ?? this.page,
        error: clearError ? null : (error ?? this.error),
      );
}

class FeedNotifier extends StateNotifier<FeedState> {
  final FeedRepository _repository;
  static const _pageSize = 20;

  FeedNotifier(this._repository) : super(const FeedState()) {
    refresh();
  }

  Future<void> refresh() async {
    state = state.copyWith(loading: true, clearError: true);
    try {
      final posts = await _repository.myFeed(page: 0, size: _pageSize);
      if (!mounted) return;
      state = FeedState(
        posts: posts,
        page: 0,
        hasMore: posts.length == _pageSize,
      );
      _repository.markSeen(posts.map((p) => p.id).toList());
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(loading: false, error: e.toString());
    }
  }

  Future<void> loadMore() async {
    if (state.loadingMore || !state.hasMore) return;
    state = state.copyWith(loadingMore: true);
    try {
      final nextPage = state.page + 1;
      final posts = await _repository.myFeed(page: nextPage, size: _pageSize);
      if (!mounted) return;
      state = state.copyWith(
        posts: [...state.posts, ...posts],
        page: nextPage,
        hasMore: posts.length == _pageSize,
        loadingMore: false,
      );
      _repository.markSeen(posts.map((p) => p.id).toList());
    } catch (_) {
      if (!mounted) return;
      state = state.copyWith(loadingMore: false);
    }
  }

  void prepend(Post post) {
    state = state.copyWith(posts: [post, ...state.posts]);
  }

  void replace(Post post) {
    state = state.copyWith(
      posts: state.posts.map((p) => p.id == post.id ? post : p).toList(),
    );
  }

  void remove(String postId) {
    state = state.copyWith(
      posts: state.posts.where((p) => p.id != postId).toList(),
    );
  }
}

final feedRepositoryProvider = Provider((ref) => FeedRepository());

final feedProvider = StateNotifierProvider<FeedNotifier, FeedState>(
  (ref) => FeedNotifier(ref.watch(feedRepositoryProvider)),
);
