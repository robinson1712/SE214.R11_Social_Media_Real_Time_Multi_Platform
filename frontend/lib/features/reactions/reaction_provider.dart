import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/enums.dart';
import '../../core/models/reaction.dart';
import 'reaction_repository.dart';

class ReactionKey {
  final TargetType targetType;
  final String targetId;
  final String targetOwnerId;

  const ReactionKey({
    required this.targetType,
    required this.targetId,
    required this.targetOwnerId,
  });

  @override
  bool operator ==(Object other) =>
      other is ReactionKey &&
      other.targetType == targetType &&
      other.targetId == targetId;

  @override
  int get hashCode => Object.hash(targetType, targetId);
}

class ReactionState {
  final ReactionSummary summary;
  final ReactionType? myReaction;
  final bool loading;

  const ReactionState({
    required this.summary,
    this.myReaction,
    this.loading = false,
  });
}

class ReactionNotifier extends StateNotifier<ReactionState> {
  final ReactionRepository _repository;
  final ReactionKey key;

  ReactionNotifier(this._repository, this.key)
      : super(ReactionState(summary: ReactionSummary.empty(), loading: true)) {
    _load();
  }

  Future<void> _load() async {
    // Fired unawaited from the constructor, so unlike a FutureProvider
    // there's no surrounding AsyncValue error boundary — an uncaught
    // exception here (e.g. reaction-service timing out while the backend
    // is still cold-starting) escapes as an unhandled Future rejection and
    // takes down the whole app via the global crash handler in main.dart,
    // even though every post card watches this on first paint. Fail
    // quietly to an empty summary instead; the reaction row just shows no
    // reactions rather than crashing the app.
    try {
      final results = await Future.wait([
        _repository.summary(key.targetType, key.targetId),
        _repository.myReaction(key.targetType, key.targetId),
      ]);
      if (!mounted) return;
      state = ReactionState(
        summary: results[0] as ReactionSummary,
        myReaction: (results[1] as Reaction?)?.type,
      );
    } catch (_) {
      if (!mounted) return;
      state = ReactionState(summary: ReactionSummary.empty(), loading: false);
    }
  }

  Future<void> react(ReactionType type) async {
    // Called fire-and-forget from ReactionPicker (no await/catch at the call
    // site), so this needs its own try/catch for the same reason as _load:
    // an uncaught failure here (network hiccup, backend momentarily down)
    // would otherwise escape as an unhandled Future rejection and crash the
    // whole app instead of just leaving this reaction unchanged.
    try {
      final previous = state;
      if (previous.myReaction == type) {
        await _repository.delete(key.targetType, key.targetId);
      } else {
        await _repository.upsert(UpsertReactionRequest(
          targetType: key.targetType,
          targetId: key.targetId,
          targetOwnerId: key.targetOwnerId,
          type: type,
        ));
      }
      await _load();
    } catch (_) {
      // Leave state as-is; the tap silently didn't take effect rather than
      // crashing the app.
    }
  }
}

final reactionRepositoryProvider = Provider((ref) => ReactionRepository());

final reactionProvider =
    StateNotifierProvider.family<ReactionNotifier, ReactionState, ReactionKey>(
  (ref, key) => ReactionNotifier(ref.watch(reactionRepositoryProvider), key),
);
