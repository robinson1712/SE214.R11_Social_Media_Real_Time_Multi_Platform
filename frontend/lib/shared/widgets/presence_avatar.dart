import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/chat/chat_provider.dart';
import 'user_inline.dart';

/// A [UserAvatar] with a green dot overlay when the user is online
/// (`GET /api/chat/presence/{userId}`), matching the online-indicator style
/// of Messenger/Zalo conversation lists.
class PresenceAvatar extends ConsumerWidget {
  final String userId;
  final double radius;

  const PresenceAvatar({super.key, required this.userId, this.radius = 22});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final online = ref.watch(presenceProvider(userId)).value ?? false;

    return Stack(
      clipBehavior: Clip.none,
      children: [
        UserAvatar(userId: userId, radius: radius),
        if (online)
          Positioned(
            right: -2,
            bottom: -2,
            child: Container(
              width: radius * 0.65,
              height: radius * 0.65,
              decoration: BoxDecoration(
                color: Colors.green,
                shape: BoxShape.circle,
                border: Border.all(color: Colors.white, width: 2.5),
              ),
            ),
          ),
      ],
    );
  }
}
