import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:image_picker/image_picker.dart';
import 'package:video_player/video_player.dart';

import '../../core/api/media_repository.dart';
import '../../core/models/enums.dart';
import '../../core/models/story.dart';
import 'story_provider.dart';

const double _kCanvasWidth = 320;
const double _kCanvasHeight = 480;

Future<void> showCreateStoryDialog(BuildContext context) {
  return showDialog(context: context, builder: (_) => const _CreateStoryDialog());
}

TextStyle _overlayTextStyle(TextOverlay o) {
  final color = _colorFromHex(o.color);
  try {
    return GoogleFonts.getFont(o.fontFamily, color: color, fontSize: o.fontSize, fontWeight: FontWeight.w600);
  } catch (_) {
    return TextStyle(color: color, fontSize: o.fontSize, fontWeight: FontWeight.w600);
  }
}

Color _colorFromHex(String hex) {
  final cleaned = hex.replaceFirst('#', '');
  final value = int.tryParse(cleaned, radix: 16) ?? 0xFFFFFF;
  return Color(0xFF000000 | value);
}

class _CreateStoryDialog extends ConsumerStatefulWidget {
  const _CreateStoryDialog();

  @override
  ConsumerState<_CreateStoryDialog> createState() => _CreateStoryDialogState();
}

class _CreateStoryDialogState extends ConsumerState<_CreateStoryDialog> {
  final _captionController = TextEditingController();
  String? _mediaUrl;
  StoryMediaType _mediaType = StoryMediaType.image;
  VideoPlayerController? _videoController;
  bool _uploading = false;
  bool _submitting = false;
  String? _error;

  final List<TextOverlay> _overlays = [];
  int? _selectedOverlay;

  @override
  void dispose() {
    _captionController.dispose();
    _videoController?.dispose();
    super.dispose();
  }

  Future<void> _pickMedia(StoryMediaType type) async {
    final picker = ImagePicker();
    final file = type == StoryMediaType.image
        ? await picker.pickImage(source: ImageSource.gallery)
        : await picker.pickVideo(source: ImageSource.gallery);
    if (file == null) return;
    setState(() => _uploading = true);
    try {
      final bytes = await file.readAsBytes();
      final url = await ref.read(mediaRepositoryProvider).uploadBytes(
            bytes: bytes,
            filename: file.name,
            purpose: MediaPurpose.story,
          );
      _videoController?.dispose();
      _videoController = null;
      if (type == StoryMediaType.video) {
        final controller = VideoPlayerController.networkUrl(Uri.parse(url));
        await controller.initialize();
        controller.setLooping(true);
        controller.setVolume(0);
        controller.play();
        _videoController = controller;
      }
      setState(() {
        _mediaUrl = url;
        _mediaType = type;
      });
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  Future<void> _openTextEditor({TextOverlay? existing, int? index}) async {
    final result = await showDialog<TextOverlay>(
      context: context,
      builder: (_) => _TextOverlayEditorDialog(initial: existing),
    );
    if (result == null) return;
    setState(() {
      if (index != null) {
        _overlays[index] = result;
      } else {
        _overlays.add(result);
        _selectedOverlay = _overlays.length - 1;
      }
    });
  }

  void _removeOverlay(int index) {
    setState(() {
      _overlays.removeAt(index);
      _selectedOverlay = null;
    });
  }

  void _dragOverlay(int index, Offset delta) {
    final o = _overlays[index];
    final nx = (o.x + delta.dx / _kCanvasWidth).clamp(0.0, 1.0);
    final ny = (o.y + delta.dy / _kCanvasHeight).clamp(0.0, 1.0);
    setState(() => _overlays[index] = o.copyWith(x: nx, y: ny));
  }

  Future<void> _submit() async {
    if (_mediaUrl == null) {
      setState(() => _error = 'Chọn ảnh hoặc video cho story trước');
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref.read(storyActionsProvider).create(CreateStoryRequest(
            mediaUrl: _mediaUrl!,
            mediaType: _mediaType,
            caption: _captionController.text.trim().isEmpty ? null : _captionController.text.trim(),
            textOverlays: _overlays,
          ));
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Widget _buildCanvas() {
    return GestureDetector(
      onTap: () => setState(() => _selectedOverlay = null),
      child: Container(
        width: _kCanvasWidth,
        height: _kCanvasHeight,
        clipBehavior: Clip.hardEdge,
        decoration: BoxDecoration(
          color: Colors.grey.shade900,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Stack(
          children: [
            if (_mediaUrl != null)
              Positioned.fill(
                child: _mediaType == StoryMediaType.video && _videoController != null
                    ? FittedBox(
                        fit: BoxFit.cover,
                        child: SizedBox(
                          width: _videoController!.value.size.width,
                          height: _videoController!.value.size.height,
                          child: VideoPlayer(_videoController!),
                        ),
                      )
                    : Image.network(_mediaUrl!, fit: BoxFit.cover),
              )
            else
              const Center(child: Icon(Icons.add_photo_alternate, size: 40, color: Colors.white54)),
            for (var i = 0; i < _overlays.length; i++) _buildOverlay(i),
          ],
        ),
      ),
    );
  }

  Widget _buildOverlay(int i) {
    final o = _overlays[i];
    final selected = _selectedOverlay == i;
    return Positioned(
      left: o.x * _kCanvasWidth - 60,
      top: o.y * _kCanvasHeight - 16,
      width: 120,
      child: GestureDetector(
        onTap: () => setState(() => _selectedOverlay = i),
        onDoubleTap: () => _openTextEditor(existing: o, index: i),
        onPanUpdate: (details) => _dragOverlay(i, details.delta),
        child: Container(
          decoration: selected
              ? BoxDecoration(border: Border.all(color: Colors.white, width: 1), borderRadius: BorderRadius.circular(4))
              : null,
          padding: const EdgeInsets.all(2),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(o.text, textAlign: TextAlign.center, style: _overlayTextStyle(o)),
              if (selected)
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    IconButton(
                      icon: const Icon(Icons.edit, color: Colors.white, size: 16),
                      onPressed: () => _openTextEditor(existing: o, index: i),
                      constraints: const BoxConstraints(),
                      padding: const EdgeInsets.all(4),
                    ),
                    IconButton(
                      icon: const Icon(Icons.delete, color: Colors.white, size: 16),
                      onPressed: () => _removeOverlay(i),
                      constraints: const BoxConstraints(),
                      padding: const EdgeInsets.all(4),
                    ),
                  ],
                ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 380),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Tạo story', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 12),
              Center(
                child: _uploading
                    ? const SizedBox(
                        width: _kCanvasWidth,
                        height: _kCanvasHeight,
                        child: Center(child: CircularProgressIndicator()))
                    : _buildCanvas(),
              ),
              const SizedBox(height: 8),
              Wrap(
                alignment: WrapAlignment.center,
                spacing: 8,
                children: [
                  OutlinedButton.icon(
                    icon: const Icon(Icons.image_outlined),
                    label: const Text('Ảnh'),
                    onPressed: _uploading ? null : () => _pickMedia(StoryMediaType.image),
                  ),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.videocam_outlined),
                    label: const Text('Video'),
                    onPressed: _uploading ? null : () => _pickMedia(StoryMediaType.video),
                  ),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.text_fields),
                    label: const Text('Thêm chữ'),
                    onPressed: _mediaUrl == null ? null : () => _openTextEditor(),
                  ),
                ],
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
                    : const Text('Đăng story (hết hạn sau 24h)'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Small dialog for typing story-overlay text and choosing its font + color.
class _TextOverlayEditorDialog extends StatefulWidget {
  final TextOverlay? initial;

  const _TextOverlayEditorDialog({this.initial});

  @override
  State<_TextOverlayEditorDialog> createState() => _TextOverlayEditorDialogState();
}

class _TextOverlayEditorDialogState extends State<_TextOverlayEditorDialog> {
  late final TextEditingController _controller =
      TextEditingController(text: widget.initial?.text ?? '');
  late String _font = widget.initial?.fontFamily ?? kStoryFonts.first;
  late String _color = widget.initial?.color ?? kStoryTextColors.first;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Chèn chữ lên story'),
      content: SizedBox(
        width: 320,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextField(
              controller: _controller,
              autofocus: true,
              maxLength: 80,
              style: TextStyle(fontFamily: _font),
              decoration: const InputDecoration(hintText: 'Nhập nội dung...'),
            ),
            const SizedBox(height: 8),
            const Text('Font chữ', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
            const SizedBox(height: 6),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: kStoryFonts.map((f) {
                final selected = f == _font;
                return ChoiceChip(
                  label: Text('Aa', style: GoogleFonts.getFont(f)),
                  selected: selected,
                  onSelected: (_) => setState(() => _font = f),
                );
              }).toList(),
            ),
            const SizedBox(height: 12),
            const Text('Màu chữ', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
            const SizedBox(height: 6),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: kStoryTextColors.map((c) {
                final selected = c == _color;
                return GestureDetector(
                  onTap: () => setState(() => _color = c),
                  child: Container(
                    width: 28,
                    height: 28,
                    decoration: BoxDecoration(
                      color: _colorFromHex(c),
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: selected ? Theme.of(context).primaryColor : Colors.grey.shade400,
                        width: selected ? 3 : 1,
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Huỷ')),
        ElevatedButton(
          onPressed: () {
            final text = _controller.text.trim();
            if (text.isEmpty) return;
            Navigator.pop(
              context,
              (widget.initial ?? const TextOverlay(text: '', fontFamily: '', color: '', x: 0.5, y: 0.5))
                  .copyWith(text: text, fontFamily: _font, color: _color),
            );
          },
          child: const Text('Xong'),
        ),
      ],
    );
  }
}
