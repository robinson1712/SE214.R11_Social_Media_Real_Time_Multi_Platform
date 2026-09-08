import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/api/media_repository.dart';
import '../../core/models/enums.dart';
import '../../core/models/reel.dart';
import 'reel_provider.dart';

Future<void> showCreateReelDialog(BuildContext context) {
  return showDialog(context: context, builder: (_) => const _CreateReelDialog());
}

class _CreateReelDialog extends ConsumerStatefulWidget {
  const _CreateReelDialog();

  @override
  ConsumerState<_CreateReelDialog> createState() => _CreateReelDialogState();
}

class _CreateReelDialogState extends ConsumerState<_CreateReelDialog> {
  final _captionController = TextEditingController();
  String? _videoUrl;
  String? _videoName;
  bool _uploading = false;
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _captionController.dispose();
    super.dispose();
  }

  Future<void> _pickVideo() async {
    final picker = ImagePicker();
    final file = await picker.pickVideo(source: ImageSource.gallery);
    if (file == null) return;
    setState(() => _uploading = true);
    try {
      final bytes = await file.readAsBytes();
      final url = await ref.read(mediaRepositoryProvider).uploadBytes(
            bytes: bytes,
            filename: file.name,
            purpose: MediaPurpose.reel,
          );
      setState(() {
        _videoUrl = url;
        _videoName = file.name;
      });
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  Future<void> _submit() async {
    if (_videoUrl == null) {
      setState(() => _error = 'Chọn video trước');
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref.read(reelActionsProvider).create(CreateReelRequest(
            videoUrl: _videoUrl!,
            caption: _captionController.text.trim().isEmpty ? null : _captionController.text.trim(),
          ));
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
        constraints: const BoxConstraints(maxWidth: 420),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Tạo reel', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                icon: _uploading
                    ? const SizedBox(height: 16, width: 16, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.video_camera_back),
                label: Text(_videoName ?? 'Chọn video'),
                onPressed: _uploading ? null : _pickVideo,
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _captionController,
                decoration: const InputDecoration(labelText: 'Chú thích (tuỳ chọn)'),
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
                    : const Text('Đăng reel'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
