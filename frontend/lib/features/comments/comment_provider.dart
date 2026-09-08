import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/comment.dart';
import '../../core/models/enums.dart';
import 'comment_repository.dart';

class CommentSectionState {
  final List<Comment> topLevel;
  final Map<String, List<Comment>> repliesByParent;
  final Set<String> expandedParents;
  final bool loading;

  const CommentSectionState({
    this.topLevel = const [],
    this.repliesByParent = const {},
    this.expandedParents = const {},
    this.loading = false,
  });

  CommentSectionState copyWith({
    List<Comment>? topLevel,
    Map<String, List<Comment>>? repliesByParent,
    Set<String>? expandedParents,
    bool? loading,
  }) =>
      CommentSectionState(
        topLevel: topLevel ?? this.topLevel,
        repliesByParent: repliesByParent ?? this.repliesByParent,
        expandedParents: expandedParents ?? this.expandedParents,
        loading: loading ?? this.loading,
      );
}

class CommentSectionNotifier extends StateNotifier<CommentSectionState> {
  final CommentRepository _repository;
  final TargetType targetType;
  final String targetId;
  final String targetOwnerId;

  CommentSectionNotifier(this._repository, this.targetType, this.targetId, this.targetOwnerId)
      : super(const CommentSectionState(loading: true)) {
    load();
  }

  Future<void> load() async {
    // Fired unawaited from the constructor once a post's comments are
    // expanded — same reasoning as ReactionNotifier._load in
    // reaction_provider.dart: with no surrounding try/catch this bypasses
    // Riverpod's AsyncValue error boundary entirely and an uncaught
    // failure (e.g. comment-service timing out) crashes the whole app via
    // the global handler in main.dart instead of just leaving this
    // section empty.
    state = state.copyWith(loading: true);
    try {
      final page = await _repository.topLevel(targetType, targetId);
      if (!mounted) return;
      state = state.copyWith(topLevel: page.content, loading: false);
    } catch (_) {
      if (!mounted) return;
      state = state.copyWith(loading: false);
    }
  }

  Future<void> addComment(String content, {String? parentCommentId}) async {
    final comment = await _repository.create(CreateCommentRequest(
      targetType: targetType,
      targetId: targetId,
      targetOwnerId: targetOwnerId,
      content: content,
      parentCommentId: parentCommentId,
    ));
    if (!mounted) return;
    if (parentCommentId == null) {
      state = state.copyWith(topLevel: [comment, ...state.topLevel]);
    } else {
      final replies = List<Comment>.from(
          state.repliesByParent[parentCommentId] ?? const []);
      replies.add(comment);
      state = state.copyWith(
        repliesByParent: {...state.repliesByParent, parentCommentId: replies},
        expandedParents: {...state.expandedParents, parentCommentId},
      );
    }
  }

  Future<void> toggleReplies(String parentId) async {
    final expanded = Set<String>.from(state.expandedParents);
    if (expanded.contains(parentId)) {
      expanded.remove(parentId);
      state = state.copyWith(expandedParents: expanded);
      return;
    }
    expanded.add(parentId);
    state = state.copyWith(expandedParents: expanded);
    if (!state.repliesByParent.containsKey(parentId)) {
      // Called fire-and-forget from the UI (no await/catch at the call
      // site) — same crash risk as load() above if this fails.
      try {
        final page = await _repository.replies(parentId);
        if (!mounted) return;
        state = state.copyWith(
          repliesByParent: {...state.repliesByParent, parentId: page.content},
        );
      } catch (_) {
        // Leave expandedParents as-is; replies just don't show up rather
        // than crashing the app.
      }
    }
  }

  Future<void> deleteComment(String id, {String? parentCommentId}) async {
    await _repository.delete(id);
    if (!mounted) return;
    if (parentCommentId == null) {
      state = state.copyWith(
        topLevel: state.topLevel.where((c) => c.id != id).toList(),
      );
    } else {
      final replies = (state.repliesByParent[parentCommentId] ?? const [])
          .where((c) => c.id != id)
          .toList();
      state = state.copyWith(
        repliesByParent: {...state.repliesByParent, parentCommentId: replies},
      );
    }
  }
}

final commentRepositoryProvider = Provider((ref) => CommentRepository());

class CommentSectionKey {
  final TargetType targetType;
  final String targetId;
  final String targetOwnerId;

  const CommentSectionKey(
      {required this.targetType, required this.targetId, required this.targetOwnerId});

  @override
  bool operator ==(Object other) =>
      other is CommentSectionKey && other.targetType == targetType && other.targetId == targetId;

  @override
  int get hashCode => Object.hash(targetType, targetId);
}

final commentSectionProvider = StateNotifierProvider.family<
    CommentSectionNotifier, CommentSectionState, CommentSectionKey>(
  (ref, key) => CommentSectionNotifier(
    ref.watch(commentRepositoryProvider),
    key.targetType,
    key.targetId,
    key.targetOwnerId,
  ),
);
