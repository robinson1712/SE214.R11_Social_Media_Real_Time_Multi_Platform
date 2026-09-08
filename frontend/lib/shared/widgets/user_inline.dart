import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/profile/user_lookup_provider.dart';
import 'avatar.dart';

/// Resolves [userId] -> display name/avatar via [userLookupProvider] and
/// renders "avatar + bold name", tappable to open that user's profile.
/// Falls back to a shortened id while the lookup is in flight or if it fails.
class UserInline extends ConsumerWidget {
  final String userId;
  final double avatarRadius;
  final TextStyle? nameStyle;
  final bool showAvatar;

  const UserInline({
    super.key,
    required this.userId,
    this.avatarRadius = 16,
    this.nameStyle,
    this.showAvatar = true,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profileAsync = ref.watch(userLookupProvider(userId));
    final name = profileAsync.when(
      data: (p) => p?.fullName ?? _shortId,
      loading: () => _shortId,
      error: (_, __) => _shortId,
    );
    final avatarUrl = profileAsync.value?.avatarUrl;
    final gender = profileAsync.value?.gender;

    return InkWell(
      onTap: () => context.push('/profile/$userId'),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (showAvatar) ...[
            Avatar(url: avatarUrl, name: name, radius: avatarRadius, gender: gender),
            const SizedBox(width: 8),
          ],
          Text(
            name,
            style: nameStyle ??
                const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
          ),
        ],
      ),
    );
  }

  String get _shortId => userId.length > 8 ? userId.substring(0, 8) : userId;
}

/// Just the avatar, no name — used where the caller renders the name
/// separately (e.g. a `ListTile`'s `leading` slot next to its own `title`).
class UserAvatar extends ConsumerWidget {
  final String userId;
  final double radius;

  const UserAvatar({super.key, required this.userId, this.radius = 20});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(userLookupProvider(userId)).value;
    return Avatar(
        url: profile?.avatarUrl,
        name: profile?.fullName ?? '',
        radius: radius,
        gender: profile?.gender);
  }
}

/// Just the display name, no avatar — used inline inside sentences
/// ("X đã chia sẻ bài viết của Y").
class UserNameText extends ConsumerWidget {
  final String userId;
  final TextStyle? style;

  const UserNameText({super.key, required this.userId, this.style});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profileAsync = ref.watch(userLookupProvider(userId));
    final name = profileAsync.value?.fullName ??
        (userId.length > 8 ? userId.substring(0, 8) : userId);
    return Text(name, style: style);
  }
}
