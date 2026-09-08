import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../shared/widgets/presence_avatar.dart';
import '../../shared/widgets/user_inline.dart';
import '../auth/auth_provider.dart';
import 'chat_provider.dart';

/// The conversation list — Messenger's own left-hand "Đoạn chat" panel.
/// Takes [selectedConversationId] so the currently-open thread can be
/// highlighted, and navigates via `context.go` (not `push`): selecting a
/// different conversation replaces which thread this same split view shows,
/// it doesn't stack a new page on top of it.
class ConversationListPanel extends ConsumerWidget {
  final String? selectedConversationId;

  const ConversationListPanel({super.key, this.selectedConversationId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(chatProvider);
    final myId = ref.watch(currentAccountIdProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Text('Đoạn chat', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
        ),
        Expanded(
          child: state.loadingConversations && state.conversations.isEmpty
              ? const Center(child: CircularProgressIndicator())
              : state.conversations.isEmpty
                  ? Padding(
                      padding: const EdgeInsets.all(24),
                      child: Text('Chưa có cuộc trò chuyện nào',
                          style: TextStyle(color: Colors.grey.shade600)),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      itemCount: state.conversations.length,
                      itemBuilder: (context, index) {
                        final c = state.conversations[index];
                        final otherId = myId == null ? '' : c.otherParticipant(myId);
                        final unread = state.unreadByConversation[c.id] ?? 0;
                        final selected = c.id == selectedConversationId;
                        return Material(
                          color: selected ? Colors.blue.shade50 : Colors.transparent,
                          borderRadius: BorderRadius.circular(10),
                          child: ListTile(
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                            leading: otherId.isEmpty
                                ? const CircleAvatar(child: Icon(Icons.chat))
                                : PresenceAvatar(userId: otherId, radius: 24),
                            title: otherId.isEmpty
                                ? const Text('Nhóm chat')
                                : UserNameText(
                                    userId: otherId,
                                    style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                                  ),
                            subtitle: Text(
                              c.lastMessagePreview ?? '(chưa có tin nhắn)',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontSize: 13,
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
                                    decoration: const BoxDecoration(
                                        color: Colors.red, shape: BoxShape.circle),
                                    child: Text('$unread',
                                        style: const TextStyle(color: Colors.white, fontSize: 11)),
                                  ),
                                ],
                              ],
                            ),
                            onTap: () => context.go('/chat/${c.id}'),
                          ),
                        );
                      },
                    ),
        ),
      ],
    );
  }
}
