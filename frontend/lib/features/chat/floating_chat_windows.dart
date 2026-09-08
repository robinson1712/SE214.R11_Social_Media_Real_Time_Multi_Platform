import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/chat.dart';
import '../../shared/widgets/presence_avatar.dart';
import '../../shared/widgets/user_inline.dart';
import '../auth/auth_provider.dart';
import 'chat_composer_bar.dart';
import 'chat_message_bubble.dart';
import 'chat_message_kind.dart';
import 'chat_provider.dart';

const double _kWindowWidth = 300;
const double _kWindowHeight = 380;
const double _kHeaderHeight = 44;
const double _kGap = 10;

/// Facebook-desktop-style floating chat windows docked to the bottom-right
/// of the viewport, stacking leftward in the order they were opened (newest
/// closest to the corner). Rendered once at the AppShell level so windows
/// persist across page navigation, same as real Messenger chat heads.
class FloatingChatWindows extends ConsumerWidget {
  const FloatingChatWindows({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final windows = ref.watch(chatProvider.select((s) => s.openWindows));
    if (windows.isEmpty) return const SizedBox.shrink();

    return Stack(
      children: [
        for (var i = 0; i < windows.length; i++)
          Positioned(
            right: 16 + (windows.length - 1 - i) * (_kWindowWidth + _kGap),
            bottom: 0,
            child: _FloatingChatWindow(key: ValueKey(windows[i]), conversationId: windows[i]),
          ),
      ],
    );
  }
}

class _FloatingChatWindow extends ConsumerStatefulWidget {
  final String conversationId;

  const _FloatingChatWindow({super.key, required this.conversationId});

  @override
  ConsumerState<_FloatingChatWindow> createState() => _FloatingChatWindowState();
}

class _FloatingChatWindowState extends ConsumerState<_FloatingChatWindow> {
  @override
  Widget build(BuildContext context) {
    final state = ref.watch(chatProvider);
    final myId = ref.watch(currentAccountIdProvider);
    final minimized = state.minimizedWindows.contains(widget.conversationId);

    Conversation? conversation;
    for (final c in state.conversations) {
      if (c.id == widget.conversationId) conversation = c;
    }
    final otherId = conversation != null && myId != null
        ? conversation.otherParticipant(myId)
        : null;
    final messages = state.messagesByConversation[widget.conversationId] ?? const [];

    return Material(
      elevation: 8,
      borderRadius: const BorderRadius.vertical(top: Radius.circular(8)),
      child: Container(
        width: _kWindowWidth,
        height: minimized ? _kHeaderHeight : _kWindowHeight,
        decoration: BoxDecoration(
          color: Colors.white,
          border: Border.all(color: Colors.grey.shade300),
          borderRadius: const BorderRadius.vertical(top: Radius.circular(8)),
        ),
        child: Column(
          children: [
            _buildHeader(context, otherId),
            if (!minimized) ...[
              const Divider(height: 1),
              Expanded(
                child: ListView.builder(
                  reverse: true,
                  padding: const EdgeInsets.all(8),
                  itemCount: messages.length,
                  itemBuilder: (context, index) {
                    final m = messages[index];
                    final mine = m.senderId == myId;
                    final isSticker = !m.deleted && isStickerMessage(m.content);
                    final showAvatar =
                        !mine && (index == 0 || messages[index - 1].senderId != m.senderId);

                    final bubble = Container(
                      margin: const EdgeInsets.symmetric(vertical: 1),
                      padding: isSticker
                          ? EdgeInsets.zero
                          : const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      constraints: const BoxConstraints(maxWidth: 190),
                      decoration: isSticker
                          ? null
                          : BoxDecoration(
                              color: mine ? Theme.of(context).primaryColor : Colors.grey.shade200,
                              borderRadius: BorderRadius.circular(12),
                            ),
                      child: ChatMessageBody(message: m, mine: mine, compact: true),
                    );

                    if (mine) {
                      return Align(alignment: Alignment.centerRight, child: bubble);
                    }
                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 1),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          SizedBox(
                            width: 20,
                            child: showAvatar ? UserAvatar(userId: m.senderId, radius: 10) : null,
                          ),
                          const SizedBox(width: 4),
                          Flexible(child: bubble),
                        ],
                      ),
                    );
                  },
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(6),
                child: ChatComposerBar(conversationId: widget.conversationId, compact: true),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, String? otherId) {
    return InkWell(
      onTap: () => ref.read(chatProvider.notifier).toggleMinimizeWindow(widget.conversationId),
      borderRadius: const BorderRadius.vertical(top: Radius.circular(8)),
      child: SizedBox(
        height: _kHeaderHeight,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10),
          child: Row(
            children: [
              if (otherId != null) ...[
                PresenceAvatar(userId: otherId, radius: 14),
                const SizedBox(width: 8),
                Expanded(
                  child: UserNameText(
                    userId: otherId,
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                  ),
                ),
              ] else
                const Expanded(
                  child: Text('Nhóm chat',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                ),
              IconButton(
                icon: const Icon(Icons.remove, size: 16),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
                tooltip: 'Thu nhỏ',
                onPressed: () =>
                    ref.read(chatProvider.notifier).toggleMinimizeWindow(widget.conversationId),
              ),
              IconButton(
                icon: const Icon(Icons.close, size: 16),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
                tooltip: 'Đóng',
                onPressed: () =>
                    ref.read(chatProvider.notifier).closeFloatingWindow(widget.conversationId),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
