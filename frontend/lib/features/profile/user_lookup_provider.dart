import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/user_profile.dart';
import 'profile_repository.dart';

/// Caches `UserProfile` lookups by id — Posts/comments/reactions only carry
/// raw author ids, so every place that renders a name/avatar watches this
/// instead of calling the repository directly, and Riverpod dedupes
/// concurrent watches of the same id into one request.
///
/// `autoDispose`d deliberately: a plain (non-autoDispose) `FutureProvider`
/// caches its result — including a caught exception turned into `null` —
/// for the rest of the app session with no retry, so a single transient
/// failure would permanently show that user as a raw id everywhere for the
/// rest of the session. `autoDispose` drops the cached value once nothing
/// is watching it (e.g. the post scrolls off-screen), so the next time
/// that user's name is needed it fetches fresh instead of replaying an old
/// failure. Transient 429s specifically are already retried transparently
/// by DioClient's interceptor — no need to duplicate that here.
final userLookupProvider =
    FutureProvider.autoDispose.family<UserProfile?, String>((ref, userId) async {
  try {
    return await ref.read(profileRepositoryProvider).getById(userId);
  } catch (_) {
    return null;
  }
});
