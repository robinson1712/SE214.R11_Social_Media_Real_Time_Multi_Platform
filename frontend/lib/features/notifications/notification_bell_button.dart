import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'notification_provider.dart';
import 'notification_tile.dart';

/// Facebook-style notification bell: a badge-counted icon that opens an
/// anchored dropdown panel (positioned the same way as FriendRequestsButton)
/// listing recent notifications as rich `NotificationTile`s.
class NotificationBellButton extends ConsumerStatefulWidget {
  const NotificationBellButton({super.key});

  @override
  ConsumerState<NotificationBellButton> createState() => _NotificationBellButtonState();
}

class _NotificationBellButtonState extends ConsumerState<NotificationBellButton> {
  Future<void> _open() async {
    final button = context.findRenderObject() as RenderBox;
    final overlay = Navigator.of(context).overlay!.context.findRenderObject() as RenderBox;
    final position = RelativeRect.fromRect(
      Rect.fromPoints(
        button.localToGlobal(Offset(0, button.size.height), ancestor: overlay),
        button.localToGlobal(button.size.bottomRight(Offset.zero), ancestor: overlay),
      ),
      Offset.zero & overlay.size,
    );

    await showMenu<void>(
      context: context,
      position: position,
      constraints: const BoxConstraints(minWidth: 340, maxWidth: 340, maxHeight: 460),
      items: [
        PopupMenuItem<void>(
          enabled: false,
          padding: EdgeInsets.zero,
          child: _NotificationPanel(onClose: () => Navigator.of(context).pop()),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(notificationProvider);

    return InkWell(
      customBorder: const CircleBorder(),
      onTap: _open,
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            const Icon(Icons.notifications_none),
            if (state.unreadCount > 0)
              Positioned(
                right: -4,
                top: -4,
                child: Container(
                  padding: const EdgeInsets.all(3),
                  decoration: const BoxDecoration(color: Colors.red, shape: BoxShape.circle),
                  child: Text('${state.unreadCount}',
                      style: const TextStyle(color: Colors.white, fontSize: 10)),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// Scrolling to the bottom of this panel loads older notifications page by
/// page — replaces the old separate "Xem tất cả" full page entirely; the
/// dropdown is now the one place notifications are browsed.
class _NotificationPanel extends ConsumerStatefulWidget {
  final VoidCallback onClose;

  const _NotificationPanel({required this.onClose});

  @override
  ConsumerState<_NotificationPanel> createState() => _NotificationPanelState();
}

class _NotificationPanelState extends ConsumerState<_NotificationPanel> {
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  void _onScroll() {
    if (!mounted) return;
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 120) {
      ref.read(notificationProvider.notifier).loadMore();
    }
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(notificationProvider);

    return SizedBox(
      width: 340,
      height: 460,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 8, 8),
            child: Row(
              children: [
                Text('Thông báo', style: Theme.of(context).textTheme.titleMedium),
                const Spacer(),
                if (state.unreadCount > 0)
                  TextButton(
                    onPressed: () => ref.read(notificationProvider.notifier).markAllRead(),
                    child: const Text('Đánh dấu đã đọc tất cả', style: TextStyle(fontSize: 12)),
                  ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: state.items.isEmpty
                ? Center(
                    child: Text('Chưa có thông báo nào', style: TextStyle(color: Colors.grey.shade600)))
                : ListView.builder(
                    controller: _scrollController,
                    itemCount: state.items.length + (state.hasMore ? 1 : 0),
                    itemBuilder: (context, i) {
                      if (i >= state.items.length) {
                        return const Padding(
                          padding: EdgeInsets.symmetric(vertical: 16),
                          child: Center(
                              child: SizedBox(
                                  width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))),
                        );
                      }
                      return NotificationTile(notification: state.items[i], onAfterTap: widget.onClose);
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
