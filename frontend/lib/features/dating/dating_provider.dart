import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/dating.dart';
import '../../core/models/enums.dart';
import 'dating_repository.dart';

final myDatingProfileProvider = FutureProvider<DatingProfile?>((ref) {
  return ref.read(datingRepositoryProvider).myProfile();
});

class CandidatesState {
  final List<CandidateResponse> candidates;
  final bool loading;
  final int index;
  final String? lastMatchUserId;

  const CandidatesState({
    this.candidates = const [],
    this.loading = false,
    this.index = 0,
    this.lastMatchUserId,
  });

  CandidatesState copyWith({
    List<CandidateResponse>? candidates,
    bool? loading,
    int? index,
    String? lastMatchUserId,
    bool clearMatch = false,
  }) =>
      CandidatesState(
        candidates: candidates ?? this.candidates,
        loading: loading ?? this.loading,
        index: index ?? this.index,
        lastMatchUserId: clearMatch ? null : (lastMatchUserId ?? this.lastMatchUserId),
      );
}

class CandidatesNotifier extends StateNotifier<CandidatesState> {
  final DatingRepository _repository;

  CandidatesNotifier(this._repository) : super(const CandidatesState(loading: true)) {
    _load();
  }

  Future<void> _load() async {
    state = state.copyWith(loading: true);
    try {
      final page = await _repository.candidates(size: 30);
      if (!mounted) return;
      state = state.copyWith(candidates: page.content, loading: false, index: 0);
    } catch (_) {
      if (!mounted) return;
      state = state.copyWith(loading: false);
    }
  }

  Future<void> refresh() => _load();

  Future<void> swipe(SwipeAction action) async {
    if (state.index >= state.candidates.length) return;
    final target = state.candidates[state.index];
    state = state.copyWith(index: state.index + 1, clearMatch: true);
    try {
      final result = await _repository.swipe(SwipeRequest(targetId: target.profile.id, action: action));
      if (!mounted) return;
      if (result.matched) {
        state = state.copyWith(lastMatchUserId: target.profile.id);
      }
    } catch (_) {
      // Best-effort — the candidate stays skipped locally either way.
    }
  }
}

final candidatesProvider = StateNotifierProvider<CandidatesNotifier, CandidatesState>(
  (ref) => CandidatesNotifier(ref.watch(datingRepositoryProvider)),
);

final matchesProvider = FutureProvider((ref) {
  return ref.read(datingRepositoryProvider).matches(size: 50);
});
