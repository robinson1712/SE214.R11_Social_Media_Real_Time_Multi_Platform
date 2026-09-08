import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/api/media_repository.dart';
import '../../core/models/enums.dart';
import '../../core/models/post.dart';
import '../../core/models/user_profile.dart';
import '../../shared/widgets/mention_text_field.dart';
import '../auth/current_user_widgets.dart';
import '../profile/profile_repository.dart';
import 'post_provider.dart';

/// Create-or-edit post dialog. Facebook-parity controls: content, media
/// (uploaded through media-service), privacy (PUBLIC/FRIENDS/CUSTOM/PRIVATE
/// — CUSTOM reveals a friend multi-picker), and tagging friends into the
/// post.
Future<void> showPostComposerDialog(
  BuildContext context, {
  Post? editing,
  String? groupId,
  String? pageId,
  VoidCallback? onPosted,
}) {
  return showDialog(
    context: context,
    builder: (_) => PostComposerDialog(
        editing: editing, groupId: groupId, pageId: pageId, onPosted: onPosted),
  );
}

class PostComposerDialog extends ConsumerStatefulWidget {
  final Post? editing;
  final String? groupId;
  final String? pageId;
  final VoidCallback? onPosted;

  const PostComposerDialog(
      {super.key, this.editing, this.groupId, this.pageId, this.onPosted});

  @override
  ConsumerState<PostComposerDialog> createState() =>
      _PostComposerDialogState();
}

class _PostComposerDialogState extends ConsumerState<PostComposerDialog> {
  late final TextEditingController _contentController;
  late Privacy _privacy;
  final List<String> _mediaUrls = [];
  final Set<String> _customAudience = {};
  final Set<String> _taggedUsers = {};
  bool _uploading = false;
  bool _submitting = false;
  String? _error;
  List<UserProfile> _friends = [];

  bool get _isEditing => widget.editing != null;

  @override
  void initState() {
    super.initState();
    final editing = widget.editing;
    _contentController = TextEditingController(text: editing?.content ?? '');
    _privacy = editing?.privacy ?? Privacy.public;
    if (editing != null) {
      _mediaUrls.addAll(editing.mediaUrls);
      _customAudience.addAll(editing.customAudienceUserIds);
      _taggedUsers.addAll(editing.taggedUserIds);
    }
    _loadFriends();
  }

  Future<void> _loadFriends() async {
    try {
      final page =
          await ref.read(profileRepositoryProvider).myFriends(size: 200);
      if (mounted) setState(() => _friends = page.content);
    } catch (_) {
      // Friend picker just stays empty; not fatal to composing a post.
    }
  }

  @override
  void dispose() {
    _contentController.dispose();
    super.dispose();
  }

  Future<void> _pickMedia() async {
    final picker = ImagePicker();
    final file = await picker.pickImage(source: ImageSource.gallery);
    if (file == null) return;
    setState(() => _uploading = true);
    try {
      final bytes = await file.readAsBytes();
      final url = await ref.read(mediaRepositoryProvider).uploadBytes(
            bytes: bytes,
            filename: file.name,
            purpose: MediaPurpose.post,
          );
      setState(() => _mediaUrls.add(url));
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  Future<void> _submit() async {
    if (_contentController.text.trim().isEmpty && _mediaUrls.isEmpty) {
      setState(() => _error = 'Viết gì đó hoặc thêm ảnh trước khi đăng');
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final actions = ref.read(postActionsProvider);
      if (_isEditing) {
        await actions.update(
          widget.editing!.id,
          UpdatePostRequest(
            content: _contentController.text.trim(),
            mediaUrls: _mediaUrls,
            privacy: _privacy,
            customAudienceUserIds:
                _privacy == Privacy.custom ? _customAudience.toList() : [],
            taggedUserIds: _taggedUsers.toList(),
          ),
        );
      } else {
        final request = CreatePostRequest(
          content: _contentController.text.trim(),
          mediaUrls: _mediaUrls,
          privacy: _privacy,
          groupId: widget.groupId,
          pageId: widget.pageId,
          customAudienceUserIds:
              _privacy == Privacy.custom ? _customAudience.toList() : [],
          taggedUserIds: _taggedUsers.toList(),
        );
        // Group/page posts don't belong in the personal home feed, so skip
        // the feed-prepending action layer and call the repository directly.
        if (widget.groupId != null || widget.pageId != null) {
          await ref.read(postRepositoryProvider).create(request);
        } else {
          await actions.create(request);
        }
      }
      widget.onPosted?.call();
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 520, maxHeight: 640),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      _isEditing ? 'Chỉnh sửa bài viết' : 'Tạo bài viết',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
              const Divider(),
              Row(
                children: [
                  const CurrentUserAvatar(),
                  const SizedBox(width: 10),
                  DropdownButton<Privacy>(
                    value: _privacy,
                    items: Privacy.values
                        .map((p) => DropdownMenuItem(
                              value: p,
                              child: Text(p.label),
                            ))
                        .toList(),
                    onChanged: (v) => setState(() => _privacy = v!),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Flexible(
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      MentionTextField(
                        controller: _contentController,
                        candidates: _friends,
                        mentionedUserIds: _taggedUsers,
                        maxLines: 5,
                        minLines: 3,
                        decoration: const InputDecoration(
                          hintText: 'Bạn đang nghĩ gì? Gõ @ để gắn thẻ bạn bè',
                        ),
                      ),
                      if (_mediaUrls.isNotEmpty) ...[
                        const SizedBox(height: 10),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: _mediaUrls
                              .map((url) => Stack(
                                    children: [
                                      ClipRRect(
                                        borderRadius:
                                            BorderRadius.circular(8),
                                        child: Image.network(url,
                                            width: 90,
                                            height: 90,
                                            fit: BoxFit.cover),
                                      ),
                                      Positioned(
                                        right: 0,
                                        top: 0,
                                        child: GestureDetector(
                                          onTap: () => setState(
                                              () => _mediaUrls.remove(url)),
                                          child: const CircleAvatar(
                                            radius: 10,
                                            backgroundColor: Colors.black54,
                                            child: Icon(Icons.close,
                                                size: 12,
                                                color: Colors.white),
                                          ),
                                        ),
                                      ),
                                    ],
                                  ))
                              .toList(),
                        ),
                      ],
                      const SizedBox(height: 12),
                      if (_privacy == Privacy.custom) ...[
                        Text('Chỉ hiện với:',
                            style: Theme.of(context).textTheme.labelLarge),
                        _FriendMultiSelect(
                          friends: _friends,
                          selected: _customAudience,
                          onChanged: (id, sel) => setState(() =>
                              sel ? _customAudience.add(id) : _customAudience.remove(id)),
                        ),
                        const SizedBox(height: 12),
                      ],
                      Text('Gắn thẻ bạn bè:',
                          style: Theme.of(context).textTheme.labelLarge),
                      _FriendMultiSelect(
                        friends: _friends,
                        selected: _taggedUsers,
                        onChanged: (id, sel) => setState(
                            () => sel ? _taggedUsers.add(id) : _taggedUsers.remove(id)),
                      ),
                      if (_error != null) ...[
                        const SizedBox(height: 8),
                        Text(_error!,
                            style: TextStyle(
                                color: Theme.of(context).colorScheme.error)),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  IconButton(
                    icon: _uploading
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.image, color: Colors.green),
                    onPressed: _uploading ? null : _pickMedia,
                  ),
                  const Spacer(),
                  ElevatedButton(
                    onPressed: _submitting ? null : _submit,
                    child: _submitting
                        ? const SizedBox(
                            height: 18,
                            width: 18,
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: Colors.white))
                        : Text(_isEditing ? 'Lưu' : 'Đăng'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _FriendMultiSelect extends StatelessWidget {
  final List<UserProfile> friends;
  final Set<String> selected;
  final void Function(String id, bool selected) onChanged;

  const _FriendMultiSelect({
    required this.friends,
    required this.selected,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    if (friends.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 6),
        child: Text('Chưa có bạn bè để chọn',
            style: TextStyle(color: Colors.grey)),
      );
    }
    return Wrap(
      spacing: 8,
      runSpacing: 4,
      children: friends.map((f) {
        final isSelected = selected.contains(f.id);
        return FilterChip(
          label: Text(f.fullName),
          selected: isSelected,
          onSelected: (sel) => onChanged(f.id, sel),
        );
      }).toList(),
    );
  }
}
