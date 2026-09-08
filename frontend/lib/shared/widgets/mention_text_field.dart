import 'package:flutter/material.dart';

import '../../core/models/user_profile.dart';
import 'avatar.dart';

/// A [TextField] with Facebook-style `@name` autocomplete: typing `@`
/// followed by letters shows a dropdown of matching [candidates] (friends)
/// directly below the field, picking one inserts their name as text and
/// records their id into [mentionedUserIds] — the caller merges that set
/// into `taggedUserIds` on submit. Purely a client-side text/id
/// association: the backend has no concept of parsing `@mentions` out of
/// free-text content, so this widget is what turns typed mentions into the
/// same `taggedUserIds` field the existing tag-picker chips write to.
///
/// Renders the suggestion list inline (pushing following content down)
/// rather than as a floating overlay — this widget is typically used inside
/// a [Dialog], and a raw `OverlayEntry` positioned via
/// `CompositedTransformFollower` turned out unreliable there (taps on a
/// suggestion silently did nothing), so inline is the reliable choice: it's
/// a normal part of the widget tree with no cross-overlay z-order or
/// focus-loss timing to get wrong.
class MentionTextField extends StatefulWidget {
  final TextEditingController controller;
  final List<UserProfile> candidates;
  final Set<String> mentionedUserIds;
  final InputDecoration? decoration;
  final int? maxLines;
  final int? minLines;
  final ValueChanged<String>? onSubmitted;
  final FocusNode? focusNode;

  const MentionTextField({
    super.key,
    required this.controller,
    required this.candidates,
    required this.mentionedUserIds,
    this.decoration,
    this.maxLines = 1,
    this.minLines,
    this.onSubmitted,
    this.focusNode,
  });

  @override
  State<MentionTextField> createState() => _MentionTextFieldState();
}

class _MentionTextFieldState extends State<MentionTextField> {
  FocusNode? _ownedFocusNode;
  int? _atIndex;
  int? _queryEnd;
  List<UserProfile> _matches = const [];

  FocusNode get _focusNode => widget.focusNode ?? (_ownedFocusNode ??= FocusNode());

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onTextChanged);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onTextChanged);
    _ownedFocusNode?.dispose();
    super.dispose();
  }

  void _onTextChanged() {
    final text = widget.controller.text;
    final cursor = widget.controller.selection.baseOffset;
    if (cursor < 0 || cursor > text.length) {
      if (_matches.isNotEmpty) setState(() => _matches = const []);
      return;
    }

    // Scan backward from the cursor for an unclosed "@token".
    var start = cursor;
    while (start > 0 && text[start - 1] != '@' && text[start - 1] != ' ' && text[start - 1] != '\n') {
      start--;
    }
    final hasAt = start > 0 && text[start - 1] == '@';
    if (!hasAt) {
      if (_matches.isNotEmpty) setState(() => _matches = const []);
      return;
    }
    final query = text.substring(start, cursor).toLowerCase();
    final matches = widget.candidates
        .where((c) => c.fullName.toLowerCase().contains(query))
        .take(6)
        .toList();

    setState(() {
      _atIndex = start - 1;
      _queryEnd = cursor;
      _matches = matches;
    });
  }

  void _pick(UserProfile user) {
    final text = widget.controller.text;
    final atIndex = _atIndex;
    final cursor = _queryEnd;
    if (atIndex == null || cursor == null || cursor > text.length) return;
    final before = text.substring(0, atIndex);
    final after = text.substring(cursor);
    final inserted = '@${user.fullName} ';
    final newText = '$before$inserted$after';
    widget.controller.value = TextEditingValue(
      text: newText,
      selection: TextSelection.collapsed(offset: before.length + inserted.length),
    );
    widget.mentionedUserIds.add(user.id);
    setState(() => _matches = const []);
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        TextField(
          controller: widget.controller,
          focusNode: _focusNode,
          decoration: widget.decoration,
          maxLines: widget.maxLines,
          minLines: widget.minLines,
          onSubmitted: widget.onSubmitted,
        ),
        if (_matches.isNotEmpty)
          Container(
            margin: const EdgeInsets.only(top: 4),
            constraints: const BoxConstraints(maxHeight: 200),
            decoration: BoxDecoration(
              border: Border.all(color: Colors.grey.shade300),
              borderRadius: BorderRadius.circular(8),
            ),
            child: ListView(
              shrinkWrap: true,
              padding: const EdgeInsets.symmetric(vertical: 4),
              children: _matches
                  .map((u) => ListTile(
                        dense: true,
                        leading: Avatar(
                            url: u.avatarUrl, name: u.fullName, radius: 14, gender: u.gender),
                        title: Text(u.fullName),
                        onTap: () => _pick(u),
                      ))
                  .toList(),
            ),
          ),
      ],
    );
  }
}
