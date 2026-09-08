import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/fanpage.dart';
import '../../core/models/page_response.dart';
import 'fanpage_repository.dart';

enum PagesTab { discover, mine }

/// Which pane the Pages section's sidebar has selected — same reasoning as
/// GroupsTab/groupsSectionTabProvider in group_provider.dart: lifted out of
/// widget state so AppShell can render the section's sidebar itself
/// (replacing the app's generic left nav — see app_shell.dart) separately
/// from the content pane.
final pagesSectionTabProvider = StateProvider<PagesTab>((ref) => PagesTab.discover);

final fanpageListProvider =
    FutureProvider.family<PageResponse<Fanpage>, String?>((ref, name) {
  return ref.read(fanpageRepositoryProvider).list(name: name);
});

final myManagedFanpagesProvider = FutureProvider<PageResponse<Fanpage>>((ref) {
  return ref.read(fanpageRepositoryProvider).myManaged(size: 100);
});

/// Pages the current user follows/likes — powers the left-nav shortcuts.
final myFollowedFanpagesProvider = FutureProvider<PageResponse<Fanpage>>((ref) {
  return ref.read(fanpageRepositoryProvider).myFollowed(size: 100);
});

final fanpageDetailProvider = FutureProvider.family<Fanpage, String>((ref, id) {
  return ref.read(fanpageRepositoryProvider).getById(id);
});

final fanpageFollowersProvider =
    FutureProvider.family<PageResponse<PageFollower>, String>((ref, pageId) {
  return ref.read(fanpageRepositoryProvider).followers(pageId, size: 100);
});

class FanpageActions {
  final Ref _ref;

  FanpageActions(this._ref);

  FanpageRepository get _repository => _ref.read(fanpageRepositoryProvider);

  Future<Fanpage> create(CreateFanpageRequest request) async {
    final page = await _repository.create(request);
    _ref.invalidate(myManagedFanpagesProvider);
    return page;
  }

  Future<void> follow(String pageId) async {
    await _repository.follow(pageId);
    _ref.invalidate(fanpageDetailProvider(pageId));
    _ref.invalidate(fanpageFollowersProvider(pageId));
    _ref.invalidate(myFollowedFanpagesProvider);
  }

  Future<void> unfollow(String pageId) async {
    await _repository.unfollow(pageId);
    _ref.invalidate(fanpageDetailProvider(pageId));
    _ref.invalidate(fanpageFollowersProvider(pageId));
    _ref.invalidate(myFollowedFanpagesProvider);
  }
}

final fanpageActionsProvider = Provider((ref) => FanpageActions(ref));
