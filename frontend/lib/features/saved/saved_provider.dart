import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/enums.dart';
import '../../core/models/page_response.dart';
import '../../core/models/saved_item.dart';
import 'saved_repository.dart';

final savedRepositoryProvider = Provider((ref) => SavedRepository());

class SaveKey {
  final TargetType targetType;
  final String targetId;

  const SaveKey({required this.targetType, required this.targetId});

  @override
  bool operator ==(Object other) =>
      other is SaveKey && other.targetType == targetType && other.targetId == targetId;

  @override
  int get hashCode => Object.hash(targetType, targetId);
}

/// Per-target "is this saved by me" toggle state — same shape as
/// ReactionNotifier (reaction_provider.dart) minus the reaction "type"
/// payload: a save is just present or absent. Loads its initial value
/// lazily on first watch, then flips optimistically-but-verified on
/// toggle so a bookmark icon reflects the real backend state rather than
/// just "did the tap fire".
class SavedNotifier extends StateNotifier<bool> {
  final SavedRepository _repository;
  final SaveKey key;

  // Without this, two taps close enough together (a real double-tap, or
  // just an impatient second click before the first request returns) both
  // read the same pre-toggle `state` — since neither await has resolved
  // yet to flip it — so both decide to do the SAME action (e.g. both call
  // save()) instead of one saving and the other undoing it. The second tap
  // then silently has no effect, which is exactly "tapping twice doesn't
  // undo the save".
  bool _pending = false;

  SavedNotifier(this._repository, this.key) : super(false) {
    _load();
  }

  Future<void> _load() async {
    try {
      final mine = await _repository.mine(key.targetType, key.targetId);
      if (!mounted) return;
      state = mine != null;
    } catch (_) {
      // Leave state at its default (not saved) rather than crashing the
      // app — same reasoning as ReactionNotifier._load.
    }
  }

  Future<void> toggle({String? targetOwnerId}) async {
    if (_pending) return;
    _pending = true;
    final wasSaved = state;
    try {
      if (wasSaved) {
        await _repository.unsave(key.targetType, key.targetId);
      } else {
        await _repository.save(SaveItemRequest(
          targetType: key.targetType,
          targetId: key.targetId,
          targetOwnerId: targetOwnerId,
        ));
      }
      if (!mounted) return;
      state = !wasSaved;
    } catch (_) {
      // Leave state unchanged; the tap silently didn't take effect rather
      // than crashing the app.
    } finally {
      _pending = false;
    }
  }
}

final savedProvider = StateNotifierProvider.family<SavedNotifier, bool, SaveKey>(
  (ref, key) => SavedNotifier(ref.watch(savedRepositoryProvider), key),
);

/// Backs the "Đã lưu" page — every saved item across all target types,
/// newest first.
final mySavedItemsProvider = FutureProvider<PageResponse<SavedItem>>((ref) {
  return ref.read(savedRepositoryProvider).listMine(size: 100);
});
