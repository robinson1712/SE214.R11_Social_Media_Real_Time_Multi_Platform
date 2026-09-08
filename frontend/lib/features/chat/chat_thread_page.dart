import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/models/chat.dart';
import '../../shared/widgets/presence_avatar.dart';
import '../../shared/widgets/user_inline.dart';
import '../auth/auth_provider.dart';
import '../profile/user_lookup_provider.dart';
import 'chat_composer_bar.dart';
import 'chat_message_bubble.dart';
import 'chat_message_kind.dart';
import 'chat_provider.dart';

/// The right-hand panel of the Messenger-style split view in
/// [ChatSplitPage] (chat_split_page.dart) — the message history, its own
/// inline header (avatar/name — not a Scaffold AppBar, since this is one
/// panel among others, not a standalone page), and the composer. No
/// Scaffold/AppBar of its own so it can sit directly inside that page's
/// Row without a second, redundant top bar.
class ChatThreadPanel extends ConsumerStatefulWidget {
  final String conversationId;

  const ChatThreadPanel({super.key, required this.conversationId});

  @override
  ConsumerState<ChatThreadPanel> createState() => _ChatThreadPanelState();
}

class _StoryReplyPreview extends StatelessWidget {
  final String? previewUrl;
  final bool mine;

  const _StoryReplyPreview({required this.previewUrl, required this.mine});

  @override
  Widget build(BuildContext context) {
    final labelColor = mine ? Colors.white70 : Colors.grey.shade600;
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (previewUrl != null && previewUrl!.isNotEmpty)
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: Image.network(previewUrl!, width: 36, height: 48, fit: BoxFit.cover),
            ),
          const SizedBox(width: 6),
          Text(mine ? 'Bạn đã trả lời story' : 'Đã trả lời story của bạn',
              style: TextStyle(fontSize: 11, fontStyle: FontStyle.italic, color: labelColor)),
        ],
      ),
    );
  }
}

/// Messenger-style start-of-conversation marker: big avatar + name centered
/// above the oldest loaded message. Purely decorative — this app has no
/// per-conversation "who created this" data to show beyond that, and no
/// end-to-end encryption to truthfully claim the way Messenger's own card
/// does, so this sticks to what's actually true about the conversation.
class _ConversationIntroCard extends ConsumerWidget {
  final String? otherId;

  const _ConversationIntroCard({required this.otherId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final name = otherId == null ? null : ref.watch(userLookupProvider(otherId!)).value?.fullName;
    return Padding(
      padding: const EdgeInsets.only(top: 8, bottom: 24),
      child: Column(
        children: [
          if (otherId != null) UserAvatar(userId: otherId!, radius: 40) else _GroupAvatarPlaceholder(),
          const SizedBox(height: 10),
          if (name != null)
            Text(name, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 17)),
          const SizedBox(height: 4),
          Text(
            name == null
                ? 'Đây là phần mở đầu cuộc trò chuyện của bạn.'
                : 'Đây là phần mở đầu cuộc trò chuyện của bạn với $name.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
          ),
        ],
      ),
    );
  }
}

class _GroupAvatarPlaceholder extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return CircleAvatar(
      radius: 40,
      backgroundColor: Colors.grey.shade300,
      child: Icon(Icons.groups, size: 36, color: Colors.grey.shade600),
    );
  }
}

class _ChatThreadPanelState extends ConsumerState<ChatThreadPanel> {
  Timer? _refreshTimer;

  // Captured once while `ref` is guaranteed valid, then used directly
  // everywhere else (including dispose()) instead of calling `ref.read`
  // again later. Riverpod's ConsumerStatefulElement invalidates `ref`
  // before State.dispose() runs, so `ref.read(...)` inside dispose() (or in
  // any callback that can fire after unmount, like a Timer or microtask)
  // throws "Cannot use 'ref' after the widget was disposed".
  late final ChatNotifier _chatNotifier;

  @override
  void initState() {
    super.initState();
    _chatNotifier = ref.read(chatProvider.notifier);
    Future.microtask(() {
      if (!mounted) return;
      _chatNotifier.openConversation(widget.conversationId);
      _chatNotifier.loadMessages(widget.conversationId);
      _chatNotifier.markRead(widget.conversationId);
    });
    // The backend has no push event for "message was read" — poll gently
    // while this thread is open so the "Đã xem" indicator isn't stuck on
    // whatever was true at the moment the page opened. Guarded with
    // `mounted` since Timer.cancel() in dispose() can't retroactively stop
    // a callback that was already dispatched in the same event-loop tick.
    _refreshTimer = Timer.periodic(const Duration(seconds: 8), (_) {
      if (!mounted) return;
      _chatNotifier.loadMessages(widget.conversationId);
    });
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    _chatNotifier.closeConversation(widget.conversationId);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(chatProvider);
    final myId = ref.watch(currentAccountIdProvider);
    final messages = state.messagesByConversation[widget.conversationId] ?? const [];
    final loading = state.loadingMessagesFor.contains(widget.conversationId);

    Conversation? conversation;
    for (final c in state.conversations) {
      if (c.id == widget.conversationId) conversation = c;
    }
    final otherId = conversation != null && myId != null
        ? conversation.otherParticipant(myId)
        : null;

    final myReceipts = myId == null ? true : ref.watch(userLookupProvider(myId)).value?.readReceiptsEnabled ?? true;
    final otherReceipts =
        otherId == null ? true : ref.watch(userLookupProvider(otherId)).value?.readReceiptsEnabled ?? true;
    final receiptsVisible = myReceipts && otherReceipts;
    final lastMineIndex = receiptsVisible && otherId != null
        ? messages.indexWhere((m) => m.senderId == myId)
        : -1;
    final lastMineSeen = lastMineIndex >= 0 && messages[lastMineIndex].readBy.contains(otherId);

    return Column(
      children: [
        Container(
          height: 60,
          padding: const EdgeInsets.symmetric(horizontal: 16),
          decoration: BoxDecoration(border: Border(bottom: BorderSide(color: Colors.grey.shade200))),
          child: Row(
            children: [
              if (otherId != null) ...[
                PresenceAvatar(userId: otherId, radius: 18),
                const SizedBox(width: 10),
                UserNameText(userId: otherId, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              ] else
                const Text('Nhóm chat', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            ],
          ),
        ),
        Expanded(
          child: loading && messages.isEmpty
              ? const Center(child: CircularProgressIndicator())
              : ListView.builder(
                  reverse: true,
                  padding: const EdgeInsets.all(12),
                  // +1 for the profile intro card — with `reverse: true`
                  // (index 0 = newest, rendered at the bottom), the LAST
                  // index ends up drawn at the very top of the scroll view,
                  // i.e. above the oldest loaded message — exactly where
                  // Messenger's own start-of-conversation card sits.
                  itemCount: messages.length + 1,
                  itemBuilder: (context, index) {
                    if (index == messages.length) {
                      return _ConversationIntroCard(otherId: otherId);
                    }
                    final m = messages[index];
                    final mine = m.senderId == myId;
                    final isSticker = !m.deleted && isStickerMessage(m.content);
                    // The list is reversed (index 0 = newest), so "last
                    // message in a consecutive run from this sender" — the
                    // one Messenger hangs the little avatar off of — is the
                    // one whose NEXT-newer neighbor (index - 1) is from
                    // someone else.
                    final showAvatar =
                        !mine && (index == 0 || messages[index - 1].senderId != m.senderId);

                    final bubble = Container(
                      margin: const EdgeInsets.symmetric(vertical: 1.5),
                      padding: isSticker
                          ? EdgeInsets.zero
                          : const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      constraints: const BoxConstraints(maxWidth: 380),
                      decoration: isSticker
                          ? null
                          : BoxDecoration(
                              color: mine ? Theme.of(context).primaryColor : Colors.grey.shade200,
                              borderRadius: BorderRadius.circular(16),
                            ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (!m.deleted && m.storyReplyId != null)
                            _StoryReplyPreview(previewUrl: m.storyReplyPreviewUrl, mine: mine),
                          ChatMessageBody(message: m, mine: mine),
                          if (m.sentAt != null)
                            Text(
                              DateFormat('HH:mm').format(m.sentAt!),
                              style: TextStyle(
                                fontSize: 10,
                                color: !isSticker && mine ? Colors.white70 : Colors.grey.shade600,
                              ),
                            ),
                        ],
                      ),
                    );

                    if (mine) {
                      return Align(alignment: Alignment.centerRight, child: bubble);
                    }
                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 1.5),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          SizedBox(
                            width: 26,
                            child: showAvatar ? UserAvatar(userId: m.senderId, radius: 13) : null,
                          ),
                          const SizedBox(width: 6),
                          Flexible(child: bubble),
                        ],
                      ),
                    );
                  },
                ),
        ),
        if (lastMineSeen)
          Padding(
            padding: const EdgeInsets.only(right: 12, bottom: 4),
            child: Align(
              alignment: Alignment.centerRight,
              child: Text('Đã xem', style: TextStyle(color: Colors.grey.shade600, fontSize: 11)),
            ),
          ),
        DecoratedBox(
          decoration: BoxDecoration(border: Border(top: BorderSide(color: Colors.grey.shade200))),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
            child: ChatComposerBar(conversationId: widget.conversationId),
          ),
        ),
      ],
    );
  }
}
