import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/models/comment.dart';
import '../../core/models/enums.dart';
import '../../shared/widgets/avatar.dart';
import '../../shared/widgets/mention_text_field.dart';
import '../../shared/widgets/user_inline.dart';
import '../auth/auth_provider.dart';
import '../auth/current_user_widgets.dart';
import '../profile/profile_provider.dart';
import '../profile/user_lookup_provider.dart';
import '../reactions/reaction_picker.dart';
import '../reactions/reaction_provider.dart';
import 'comment_provider.dart';

class CommentSection extends ConsumerStatefulWidget {
  final TargetType targetType;
  final String targetId;
  final String targetOwnerId;

  const CommentSection({
    super.key,
    this.targetType = TargetType.post,
    required this.targetId,
    required this.targetOwnerId,
  });

  @override
  ConsumerState<CommentSection> createState() => _CommentSectionState();
}

class _CommentSectionState extends ConsumerState<CommentSection> {
  final _controller = TextEditingController();
  final _mentioned = <String>{};

  CommentSectionKey get _key => CommentSectionKey(
      targetType: widget.targetType, targetId: widget.targetId, targetOwnerId: widget.targetOwnerId);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final text = _controller.text.trim();
    if (text.isEmpty) return;
    _controller.clear();
    _mentioned.clear();
    await ref.read(commentSectionProvider(_key).notifier).addComment(text);
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(commentSectionProvider(_key));
    final myId = ref.watch(currentAccountIdProvider);
    final friends = ref.watch(myFriendsListProvider).value ?? const [];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const CurrentUserAvatar(radius: 16),
            const SizedBox(width: 8),
            Expanded(
              child: MentionTextField(
                controller: _controller,
                candidates: friends,
                mentionedUserIds: _mentioned,
                decoration: const InputDecoration(
                  hintText: 'Viết bình luận... (gõ @ để nhắc bạn bè)',
                  isDense: true,
                ),
                onSubmitted: (_) => _submit(),
              ),
            ),
            IconButton(
              icon: const Icon(Icons.send, size: 18),
              onPressed: _submit,
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (state.loading)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 8),
            child: Center(
                child: SizedBox(
                    height: 16, width: 16, child: CircularProgressIndicator(strokeWidth: 2))),
          )
        else
          ...state.topLevel.map(
            (c) => _CommentTile(
              comment: c,
              targetOwnerId: widget.targetOwnerId,
              myId: myId,
              sectionKey: _key,
            ),
          ),
      ],
    );
  }
}

class _CommentTile extends ConsumerWidget {
  final Comment comment;
  final String targetOwnerId;
  final String? myId;
  final CommentSectionKey sectionKey;
  final String? parentId;

  const _CommentTile({
    required this.comment,
    required this.targetOwnerId,
    required this.myId,
    required this.sectionKey,
    this.parentId,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(commentSectionProvider(sectionKey));
    final replies = state.repliesByParent[comment.id] ?? const [];
    final expanded = state.expandedParents.contains(comment.id);
    final replyKey = ReactionKey(
      targetType: TargetType.comment,
      targetId: comment.id,
      targetOwnerId: comment.authorId,
    );

    return Padding(
      padding: const EdgeInsets.only(bottom: 10, left: 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _CommentAvatar(userId: comment.authorId),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 8),
                      decoration: BoxDecoration(
                        color: const Color(0xFFF0F2F5),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          UserNameText(
                            userId: comment.authorId,
                            style: const TextStyle(
                                fontWeight: FontWeight.bold, fontSize: 13),
                          ),
                          Text(comment.content),
                        ],
                      ),
                    ),
                    Row(
                      children: [
                        if (comment.createdAt != null)
                          Padding(
                            padding: const EdgeInsets.only(left: 8, right: 12),
                            child: Text(
                              DateFormat('dd/MM HH:mm').format(comment.createdAt!),
                              style: TextStyle(
                                  color: Colors.grey.shade600, fontSize: 12),
                            ),
                          ),
                        SizedBox(
                          height: 28,
                          child: ReactionPicker(reactionKey: replyKey),
                        ),
                        TextButton(
                          onPressed: () => ref
                              .read(commentSectionProvider(sectionKey).notifier)
                              .toggleReplies(comment.id),
                          child: Text(expanded ? 'Ẩn trả lời' : 'Trả lời'),
                        ),
                        if (myId == comment.authorId)
                          IconButton(
                            iconSize: 16,
                            icon: const Icon(Icons.delete_outline),
                            onPressed: () => ref
                                .read(commentSectionProvider(sectionKey).notifier)
                                .deleteComment(comment.id,
                                    parentCommentId: parentId),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
          if (expanded)
            Padding(
              padding: const EdgeInsets.only(left: 40),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ...replies.map((r) => _CommentTile(
                        comment: r,
                        targetOwnerId: targetOwnerId,
                        myId: myId,
                        sectionKey: sectionKey,
                        parentId: comment.id,
                      )),
                  _ReplyBox(
                    targetOwnerId: targetOwnerId,
                    parentCommentId: comment.id,
                    sectionKey: sectionKey,
                    replyToUserId: comment.authorId,
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _ReplyBox extends ConsumerStatefulWidget {
  final String targetOwnerId;
  final String parentCommentId;
  final CommentSectionKey sectionKey;

  /// Author of the comment/reply this box appears under — Facebook-style,
  /// clicking "Trả lời" pre-fills the box with `@theirName ` so the reply
  /// reads as addressed to them.
  final String replyToUserId;

  const _ReplyBox({
    required this.targetOwnerId,
    required this.parentCommentId,
    required this.sectionKey,
    required this.replyToUserId,
  });

  @override
  ConsumerState<_ReplyBox> createState() => _ReplyBoxState();
}

class _ReplyBoxState extends ConsumerState<_ReplyBox> {
  final _controller = TextEditingController();
  final _mentioned = <String>{};
  final _focusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _prefillMention();
  }

  Future<void> _prefillMention() async {
    final profile = await ref.read(userLookupProvider(widget.replyToUserId).future);
    if (!mounted || profile == null) return;
    final mention = '@${profile.fullName} ';
    _controller.value = TextEditingValue(
      text: mention,
      selection: TextSelection.collapsed(offset: mention.length),
    );
    _mentioned.add(widget.replyToUserId);
    _focusNode.requestFocus();
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final friends = ref.watch(myFriendsListProvider).value ?? const [];
    return Row(
      children: [
        Expanded(
          child: MentionTextField(
            controller: _controller,
            focusNode: _focusNode,
            candidates: friends,
            mentionedUserIds: _mentioned,
            decoration: const InputDecoration(
              hintText: 'Viết trả lời... (gõ @ để nhắc bạn bè)',
              isDense: true,
            ),
            onSubmitted: (_) => _submit(),
          ),
        ),
        IconButton(
          icon: const Icon(Icons.send, size: 16),
          onPressed: _submit,
        ),
      ],
    );
  }

  Future<void> _submit() async {
    final text = _controller.text.trim();
    if (text.isEmpty) return;
    _controller.clear();
    _mentioned.clear();
    await ref
        .read(commentSectionProvider(widget.sectionKey).notifier)
        .addComment(text, parentCommentId: widget.parentCommentId);
  }
}

class _CommentAvatar extends ConsumerWidget {
  final String userId;

  const _CommentAvatar({required this.userId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(userLookupProvider(userId)).value;
    return Avatar(
        url: profile?.avatarUrl,
        name: profile?.fullName ?? '',
        radius: 16,
        gender: profile?.gender);
  }
}
