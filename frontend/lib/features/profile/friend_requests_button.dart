import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/friendship.dart';
import '../../shared/widgets/user_inline.dart';
import 'profile_provider.dart';

/// Top-bar icon for incoming friend requests (`GET
/// /api/users/me/friend-requests`), with accept/decline actions inline.
///
/// Fetches fresh on every tap (rather than relying on `PopupMenuButton`'s
/// itemBuilder, which runs synchronously off whatever the provider already
/// had cached) so a request that arrived after the icon was first built —
/// e.g. pushed in via the real-time notification socket — always shows up
/// the next time this is opened, not just after some unrelated rebuild.
class FriendRequestsButton extends ConsumerStatefulWidget {
  const FriendRequestsButton({super.key});

  @override
  ConsumerState<FriendRequestsButton> createState() => _FriendRequestsButtonState();
}

class _FriendRequestsButtonState extends ConsumerState<FriendRequestsButton> {
  bool _loading = false;

  Future<void> _open() async {
    if (_loading) return;
    setState(() => _loading = true);
    List<Friendship> requests;
    try {
      requests = await ref.refresh(pendingFriendRequestsProvider.future);
    } catch (_) {
      requests = const [];
    }
    if (!mounted) return;
    setState(() => _loading = false);

    final button = context.findRenderObject() as RenderBox;
    final overlay = Navigator.of(context).overlay!.context.findRenderObject() as RenderBox;
    final position = RelativeRect.fromRect(
      Rect.fromPoints(
        button.localToGlobal(Offset(0, button.size.height), ancestor: overlay),
        button.localToGlobal(button.size.bottomRight(Offset.zero), ancestor: overlay),
      ),
      Offset.zero & overlay.size,
    );

    if (requests.isEmpty) {
      await showMenu<void>(
        context: context,
        position: position,
        items: const [
          PopupMenuItem(enabled: false, child: Text('Không có lời mời kết bạn nào')),
        ],
      );
      return;
    }

    await showMenu<void>(
      context: context,
      position: position,
      items: requests
          .map((r) => PopupMenuItem<void>(
                enabled: false,
                child: SizedBox(
                  width: 260,
                  child: Row(
                    children: [
                      Expanded(child: UserInline(userId: r.requesterId, avatarRadius: 14)),
                      IconButton(
                        icon: const Icon(Icons.check, color: Colors.green, size: 18),
                        onPressed: () {
                          ref.read(friendshipActionsProvider).accept(r.id);
                          Navigator.of(context).pop();
                        },
                      ),
                      IconButton(
                        icon: const Icon(Icons.close, color: Colors.red, size: 18),
                        onPressed: () {
                          ref.read(friendshipActionsProvider).decline(r.id);
                          Navigator.of(context).pop();
                        },
                      ),
                    ],
                  ),
                ),
              ))
          .toList(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final count = ref.watch(pendingFriendRequestsProvider).value?.length ?? 0;

    return InkWell(
      customBorder: const CircleBorder(),
      onTap: _open,
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            _loading
                ? const SizedBox(
                    height: 24, width: 24, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.people_alt_outlined),
            if (!_loading && count > 0)
              Positioned(
                right: -4,
                top: -4,
                child: Container(
                  padding: const EdgeInsets.all(3),
                  decoration: const BoxDecoration(color: Colors.red, shape: BoxShape.circle),
                  child: Text('$count',
                      style: const TextStyle(color: Colors.white, fontSize: 10)),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
