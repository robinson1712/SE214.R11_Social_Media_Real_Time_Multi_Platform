import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../shared/widgets/presence_avatar.dart';
import '../../shared/widgets/user_inline.dart';
import '../auth/auth_provider.dart';
import 'chat_provider.dart';

/// Facebook-style Messenger icon: click opens an anchored dropdown listing
/// conversations (positioned the same way as NotificationBellButton);
/// picking one opens a floating chat window (see FloatingChatWindows)
/// instead of navigating away from whatever page is open.
class ChatDropdownButton extends ConsumerStatefulWidget {
  const ChatDropdownButton({super.key});

  @override
  ConsumerState<ChatDropdownButton> createState() => _ChatDropdownButtonState();
}

class _ChatDropdownButtonState extends ConsumerState<ChatDropdownButton> {
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
          child: _ChatDropdownPanel(onClose: () => Navigator.of(context).pop()),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final unread = ref.watch(chatUnreadCountProvider);

    return InkWell(
      customBorder: const CircleBorder(),
      onTap: _open,
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            const Icon(Icons.chat_bubble_outline),
            if (unread > 0)
              Positioned(
                right: -4,
                top: -4,
                child: Container(
                  padding: const EdgeInsets.all(3),
                  constraints: const BoxConstraints(minWidth: 16, minHeight: 16),
                  alignment: Alignment.center,
                  decoration: const BoxDecoration(color: Colors.red, shape: BoxShape.circle),
                  child: Text(
                    unread > 99 ? '99+' : '$unread',
                    style: const TextStyle(color: Colors.white, fontSize: 10),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _ChatDropdownPanel extends ConsumerWidget {
  final VoidCallback onClose;

  const _ChatDropdownPanel({required this.onClose});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(chatProvider);
    final myId = ref.watch(currentAccountIdProvider);

    return SizedBox(
      width: 340,
      height: 460,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 8, 8),
            child: Text('Đoạn chat', style: Theme.of(context).textTheme.titleMedium),
          ),
          const Divider(height: 1),
          Expanded(
            child: state.conversations.isEmpty
                ? Center(
                    child: Text('Chưa có cuộc trò chuyện nào',
                        style: TextStyle(color: Colors.grey.shade600)))
                : ListView.builder(
                    itemCount: state.conversations.length,
                    itemBuilder: (context, i) {
                      final c = state.conversations[i];
                      final otherId = myId == null ? '' : c.otherParticipant(myId);
                      final unread = state.unreadByConversation[c.id] ?? 0;
                      return ListTile(
                        leading: otherId.isEmpty
                            ? const CircleAvatar(child: Icon(Icons.chat))
                            : PresenceAvatar(userId: otherId, radius: 22),
                        title:
                            otherId.isEmpty ? const Text('Nhóm chat') : UserNameText(userId: otherId),
                        subtitle: Text(
                          c.lastMessagePreview ?? '(chưa có tin nhắn)',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontWeight: unread > 0 ? FontWeight.bold : FontWeight.normal,
                            color: unread > 0 ? Colors.black87 : Colors.grey.shade600,
                          ),
                        ),
                        trailing: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            if (c.lastMessageAt != null)
                              Text(DateFormat('HH:mm').format(c.lastMessageAt!),
                                  style: TextStyle(color: Colors.grey.shade600, fontSize: 11)),
                            if (unread > 0) ...[
                              const SizedBox(height: 4),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration:
                                    const BoxDecoration(color: Colors.red, shape: BoxShape.circle),
                                child: Text('$unread',
                                    style: const TextStyle(color: Colors.white, fontSize: 11)),
                              ),
                            ],
                          ],
                        ),
                        onTap: () {
                          ref.read(chatProvider.notifier).openFloatingWindow(c.id);
                          onClose();
                        },
                      );
                    },
                  ),
          ),
          const Divider(height: 1),
          TextButton(
            onPressed: () {
              onClose();
              context.go('/chat');
            },
            child: const Text('Xem tất cả trong Messenger'),
          ),
        ],
      ),
    );
  }
}
