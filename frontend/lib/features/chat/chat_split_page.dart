import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'chat_thread_page.dart';
import 'conversation_list_panel.dart';

/// Messenger-style split view: a fixed conversation list on the left, the
/// open thread on the right — both visible at once, replacing what used to
/// be two separate full pages (a conversations list page, and a standalone
/// thread page you'd navigate to and from). Used for both `/chat`
/// ([conversationId] null — list only, empty-state placeholder on the
/// right) and `/chat/:id` (that conversation opened on the right).
///
/// Below [_kNarrowBreakpoint] (the space actually available to this page —
/// which, nested inside AppShell's own optional side rails, can be
/// narrower than the browser viewport) there isn't room for both panels
/// side by side, so it falls back to showing one at a time, like Messenger
/// does on a phone-width screen: the list when no conversation is open, or
/// the thread (with a back button to return to the list) when one is.
class ChatSplitPage extends StatelessWidget {
  final String? conversationId;

  static const double _kNarrowBreakpoint = 760;
  static const double _kListPanelWidth = 340;

  const ChatSplitPage({super.key, this.conversationId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF0F2F5),
      body: LayoutBuilder(
        builder: (context, constraints) {
          if (constraints.maxWidth < _kNarrowBreakpoint) {
            return _buildNarrow(context);
          }
          return _buildSplit(context);
        },
      ),
    );
  }

  Widget _buildSplit(BuildContext context) {
    return Row(
      children: [
        Container(
          width: _kListPanelWidth,
          decoration: BoxDecoration(
            color: Colors.white,
            border: Border(right: BorderSide(color: Colors.grey.shade300)),
          ),
          child: SafeArea(child: ConversationListPanel(selectedConversationId: conversationId)),
        ),
        Expanded(
          child: conversationId == null
              ? const _EmptyThreadPlaceholder()
              : ColoredBox(
                  color: Colors.white,
                  child: SafeArea(
                    child: ChatThreadPanel(key: ValueKey(conversationId), conversationId: conversationId!),
                  ),
                ),
        ),
      ],
    );
  }

  Widget _buildNarrow(BuildContext context) {
    if (conversationId == null) {
      return SafeArea(child: ConversationListPanel(selectedConversationId: null));
    }
    return ColoredBox(
      color: Colors.white,
      child: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.arrow_back),
                    tooltip: 'Quay lại danh sách',
                    onPressed: () => context.go('/chat'),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ChatThreadPanel(key: ValueKey(conversationId), conversationId: conversationId!),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyThreadPlaceholder extends StatelessWidget {
  const _EmptyThreadPlaceholder();

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: Colors.white,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.forum_outlined, size: 72, color: Colors.grey.shade300),
            const SizedBox(height: 16),
            Text('Chọn một cuộc trò chuyện để bắt đầu nhắn tin',
                style: TextStyle(color: Colors.grey.shade600, fontSize: 15)),
          ],
        ),
      ),
    );
  }
}
