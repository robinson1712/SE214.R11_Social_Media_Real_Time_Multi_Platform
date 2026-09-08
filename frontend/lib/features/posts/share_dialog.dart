import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/models/enums.dart';
import '../../core/models/post.dart';
import 'post_provider.dart';

Future<void> showShareDialog(BuildContext context, Post post) {
  return showDialog(
    context: context,
    builder: (_) => ShareDialog(post: post),
  );
}

/// Same flow as [showShareDialog], but for sharing a reel to the feed as a
/// new post (post-service's `POST /api/posts/share-reel/{reelId}` — see
/// PostService.shareReel) rather than resharing an existing post.
Future<void> showReelShareDialog(BuildContext context, String reelId) {
  return showDialog(
    context: context,
    builder: (_) => _ReelShareDialog(reelId: reelId),
  );
}

class _ReelShareDialog extends ConsumerStatefulWidget {
  final String reelId;

  const _ReelShareDialog({required this.reelId});

  @override
  ConsumerState<_ReelShareDialog> createState() => _ReelShareDialogState();
}

class _ReelShareDialogState extends ConsumerState<_ReelShareDialog> {
  final _controller = TextEditingController();
  Privacy _privacy = Privacy.public;
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref.read(postActionsProvider).shareReel(
            widget.reelId,
            ShareRequest(
              content: _controller.text.trim().isEmpty ? null : _controller.text.trim(),
              privacy: _privacy,
            ),
          );
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
        constraints: const BoxConstraints(maxWidth: 480),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text('Chia sẻ reel', style: Theme.of(context).textTheme.titleLarge),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
              const Divider(),
              DropdownButton<Privacy>(
                value: _privacy,
                items: Privacy.values.map((p) => DropdownMenuItem(value: p, child: Text(p.label))).toList(),
                onChanged: (v) => setState(() => _privacy = v!),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _controller,
                maxLines: 3,
                decoration: const InputDecoration(hintText: 'Nói gì đó về reel này...'),
              ),
              if (_error != null) ...[
                const SizedBox(height: 8),
                Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ],
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: _submitting ? null : _submit,
                child: _submitting
                    ? const SizedBox(
                        height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : const Text('Chia sẻ ngay'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class ShareDialog extends ConsumerStatefulWidget {
  final Post post;

  const ShareDialog({super.key, required this.post});

  @override
  ConsumerState<ShareDialog> createState() => _ShareDialogState();
}

class _ShareDialogState extends ConsumerState<ShareDialog> {
  final _controller = TextEditingController();
  Privacy _privacy = Privacy.public;
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref.read(postActionsProvider).share(
            widget.post.id,
            ShareRequest(
              content: _controller.text.trim().isEmpty
                  ? null
                  : _controller.text.trim(),
              privacy: _privacy,
            ),
          );
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
        constraints: const BoxConstraints(maxWidth: 480),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text('Chia sẻ bài viết',
                        style: Theme.of(context).textTheme.titleLarge),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
              const Divider(),
              DropdownButton<Privacy>(
                value: _privacy,
                items: Privacy.values
                    .map((p) => DropdownMenuItem(value: p, child: Text(p.label)))
                    .toList(),
                onChanged: (v) => setState(() => _privacy = v!),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _controller,
                maxLines: 3,
                decoration: const InputDecoration(
                  hintText: 'Nói gì đó về bài viết này...',
                ),
              ),
              if (_error != null) ...[
                const SizedBox(height: 8),
                Text(_error!,
                    style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ],
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: _submitting ? null : _submit,
                child: _submitting
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white))
                    : const Text('Chia sẻ ngay'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
