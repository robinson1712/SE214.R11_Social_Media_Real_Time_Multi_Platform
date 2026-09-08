import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../shared/widgets/avatar.dart';
import '../profile/user_lookup_provider.dart';
import 'auth_provider.dart';

/// Avatar for the currently logged-in user — used in composer/comment
/// input boxes where "who is typing" should be obvious at a glance.
class CurrentUserAvatar extends ConsumerWidget {
  final double radius;

  const CurrentUserAvatar({super.key, this.radius = 18});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final account = ref.watch(authProvider).account;
    if (account == null) return Avatar(name: '', radius: radius);

    final profileAsync = ref.watch(userLookupProvider(account.id));
    final profile = profileAsync.value;
    return Avatar(
      url: profile?.avatarUrl,
      name: profile?.fullName ?? account.email,
      radius: radius,
      gender: profile?.gender,
    );
  }
}
