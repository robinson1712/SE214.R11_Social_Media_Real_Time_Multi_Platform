import 'dart:async';
import 'dart:typed_data';

import 'package:emoji_picker_flutter/emoji_picker_flutter.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http_parser/http_parser.dart';
import 'package:image_picker/image_picker.dart';
import 'package:record/record.dart';

import '../../core/api/media_repository.dart';
import '../../core/models/enums.dart';
import 'chat_message_kind.dart';
import 'chat_provider.dart';

/// A small, fixed set of large single emoji sent sticker-style (see
/// [stickerMarker] in chat_message_kind.dart) — this app has no licensed
/// sticker-pack assets to bundle, so "stickers" are large borderless emoji
/// rather than drawn artwork, same visual treatment Messenger/Telegram give
/// a lone emoji message.
const _stickerEmojis = [
  '👍', '❤️', '😂', '😮', '😢', '😡',
  '🎉', '🥳', '😍', '🤗', '👏', '🙏',
  '😴', '🤔', '😱', '🥰', '😭', '🔥',
  '💯', '✨', '🎂', '🐰', '🐱', '🐶',
];

enum _Panel { none, emoji, sticker }

/// Shared Messenger-style composer: text + emoji picker, image/video/file
/// attachments, voice recording, stickers. Used by both [ChatThreadPanel]
/// (full width) and the floating chat windows (narrow — pass
/// `compact: true` to shrink icon sizing so the row doesn't overflow).
class ChatComposerBar extends ConsumerStatefulWidget {
  final String conversationId;
  final bool compact;

  const ChatComposerBar({super.key, required this.conversationId, this.compact = false});

  @override
  ConsumerState<ChatComposerBar> createState() => _ChatComposerBarState();
}

class _ChatComposerBarState extends ConsumerState<ChatComposerBar> {
  final _controller = TextEditingController();
  final _recorder = AudioRecorder();
  _Panel _panel = _Panel.none;
  bool _uploading = false;

  bool _recording = false;
  DateTime? _recordStart;
  Duration _recordElapsed = Duration.zero;
  Timer? _recordTicker;
  StreamSubscription<Uint8List>? _recordSub;
  final BytesBuilder _recordBuffer = BytesBuilder();

  @override
  void dispose() {
    _controller.dispose();
    _recordTicker?.cancel();
    _recordSub?.cancel();
    _recorder.dispose();
    super.dispose();
  }

  /// In the full-page thread, the panel just grows the composer inline —
  /// the page scaffold has room. The floating windows are a fixed-height
  /// box (see `_kWindowHeight` in floating_chat_windows.dart), so growing
  /// the composer there would overflow it; opening the same picker in a
  /// bottom sheet instead keeps the window's height untouched.
  void _togglePanel(_Panel panel) {
    if (widget.compact) {
      showModalBottomSheet(
        context: context,
        builder: (sheetContext) => SizedBox(
          height: 320,
          child: panel == _Panel.emoji
              ? _buildEmojiPicker(320)
              : _buildStickerGrid(320, afterPick: () => Navigator.pop(sheetContext)),
        ),
      );
      return;
    }
    setState(() => _panel = _panel == panel ? _Panel.none : panel);
  }

  Widget _buildEmojiPicker(double height) {
    return SizedBox(
      height: height,
      child: EmojiPicker(
        textEditingController: _controller,
        config: Config(
          emojiViewConfig: EmojiViewConfig(emojiSizeMax: widget.compact ? 22 : 26),
        ),
      ),
    );
  }

  Widget _buildStickerGrid(double height, {VoidCallback? afterPick}) {
    return SizedBox(
      height: height,
      child: GridView.builder(
        padding: const EdgeInsets.all(8),
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 6),
        itemCount: _stickerEmojis.length,
        itemBuilder: (context, index) => InkWell(
          onTap: () {
            _sendSticker(_stickerEmojis[index]);
            afterPick?.call();
          },
          child: Center(child: Text(_stickerEmojis[index], style: const TextStyle(fontSize: 26))),
        ),
      ),
    );
  }

  void _sendText() {
    final text = _controller.text.trim();
    if (text.isEmpty) return;
    ref.read(chatProvider.notifier).sendMessage(widget.conversationId, text);
    _controller.clear();
  }

  void _sendSticker(String emoji) {
    ref.read(chatProvider.notifier).sendMessage(widget.conversationId, '$stickerMarker$emoji');
    setState(() => _panel = _Panel.none);
  }

  Future<void> _pickAndSendPhotoOrVideo() async {
    final picker = ImagePicker();
    final choice = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => SafeArea(
        child: Wrap(children: [
          ListTile(
            leading: const Icon(Icons.image_outlined),
            title: const Text('Ảnh'),
            onTap: () => Navigator.pop(context, 'image'),
          ),
          ListTile(
            leading: const Icon(Icons.videocam_outlined),
            title: const Text('Video'),
            onTap: () => Navigator.pop(context, 'video'),
          ),
        ]),
      ),
    );
    if (choice == null) return;
    final XFile? file = choice == 'image'
        ? await picker.pickImage(source: ImageSource.gallery)
        : await picker.pickVideo(source: ImageSource.gallery);
    if (file == null) return;
    await _uploadAndSend(bytes: await file.readAsBytes(), filename: file.name);
  }

  Future<void> _pickAndSendFile() async {
    final result = await FilePicker.platform.pickFiles(withData: true);
    final files = result?.files;
    final file = files != null && files.isNotEmpty ? files.first : null;
    if (file?.bytes == null) return;
    await _uploadAndSend(bytes: file!.bytes!, filename: file.name);
  }

  Future<void> _uploadAndSend({
    required List<int> bytes,
    required String filename,
    MediaType? contentType,
    String? textContent,
  }) async {
    setState(() => _uploading = true);
    try {
      final url = await ref.read(mediaRepositoryProvider).uploadBytes(
            bytes: bytes,
            filename: filename,
            purpose: MediaPurpose.chat,
            contentType: contentType,
          );
      if (!mounted) return;
      ref.read(chatProvider.notifier).sendMessage(widget.conversationId, textContent, mediaUrl: url);
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Gửi tệp đính kèm thất bại')));
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  Future<void> _startRecording() async {
    if (!await _recorder.hasPermission()) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Cần quyền truy cập micro để ghi âm')));
      return;
    }
    _recordBuffer.clear();
    final stream = await _recorder.startStream(const RecordConfig(encoder: AudioEncoder.opus));
    _recordSub = stream.listen(_recordBuffer.add);
    _recordStart = DateTime.now();
    _recordTicker = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() => _recordElapsed = DateTime.now().difference(_recordStart!));
    });
    setState(() {
      _recording = true;
      _recordElapsed = Duration.zero;
    });
  }

  Future<void> _stopRecording({required bool send}) async {
    _recordTicker?.cancel();
    await _recorder.stop();
    await _recordSub?.cancel();
    final elapsed = _recordElapsed;
    setState(() => _recording = false);
    if (!send) {
      _recordBuffer.clear();
      return;
    }
    final bytes = _recordBuffer.takeBytes();
    // Below ~1s is almost always an accidental tap, not a real message.
    if (bytes.isEmpty || elapsed.inMilliseconds < 800) return;
    await _uploadAndSend(
      bytes: bytes,
      filename: 'voice-message.weba',
      contentType: MediaType('audio', 'webm'),
    );
  }

  @override
  Widget build(BuildContext context) {
    final iconSize = widget.compact ? 18.0 : 22.0;
    // Compact (floating window) keeps a tight tap target so the row fits a
    // ~300px-wide window; the full-page thread gets normal-sized buttons
    // with breathing room between them instead of every icon's own padding
    // being forced to zero (that was making them all look glued together).
    final constraints = widget.compact
        ? const BoxConstraints(minWidth: 30, minHeight: 30)
        : const BoxConstraints(minWidth: 40, minHeight: 40);
    final buttonPadding = widget.compact ? EdgeInsets.zero : const EdgeInsets.all(8);
    final padding = widget.compact ? EdgeInsets.zero : const EdgeInsets.symmetric(horizontal: 4);

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (_recording)
          _buildRecordingRow(context)
        else
          _buildInputRow(iconSize, constraints, buttonPadding, padding),
        // Only reachable when !compact — _togglePanel opens a bottom sheet
        // instead of setting _panel for the fixed-height floating windows.
        if (_panel == _Panel.emoji) _buildEmojiPicker(260),
        if (_panel == _Panel.sticker) _buildStickerGrid(200),
      ],
    );
  }

  Widget _buildRecordingRow(BuildContext context) {
    final minutes = _recordElapsed.inMinutes.toString().padLeft(2, '0');
    final seconds = (_recordElapsed.inSeconds % 60).toString().padLeft(2, '0');
    return Padding(
      padding: widget.compact ? const EdgeInsets.symmetric(vertical: 4) : const EdgeInsets.all(8),
      child: Row(
        children: [
          IconButton(
            icon: const Icon(Icons.delete_outline, color: Colors.red),
            iconSize: widget.compact ? 18 : 22,
            tooltip: 'Huỷ',
            onPressed: () => _stopRecording(send: false),
          ),
          const Icon(Icons.fiber_manual_record, color: Colors.red, size: 14),
          const SizedBox(width: 6),
          Text('$minutes:$seconds', style: TextStyle(fontSize: widget.compact ? 12 : 14)),
          const Spacer(),
          IconButton(
            icon: const Icon(Icons.send),
            iconSize: widget.compact ? 18 : 22,
            tooltip: 'Gửi',
            onPressed: () => _stopRecording(send: true),
          ),
        ],
      ),
    );
  }

  Widget _buildInputRow(
    double iconSize,
    BoxConstraints constraints,
    EdgeInsets buttonPadding,
    EdgeInsets padding,
  ) {
    final gap = widget.compact ? const SizedBox(width: 0) : const SizedBox(width: 2);
    return Padding(
      padding: padding,
      child: Row(
        children: [
          IconButton(
            icon: const Icon(Icons.attach_file),
            iconSize: iconSize,
            padding: buttonPadding,
            constraints: constraints,
            tooltip: 'Đính kèm',
            onPressed: _uploading
                ? null
                : () => showModalBottomSheet(
                      context: context,
                      builder: (context) => SafeArea(
                        child: Wrap(children: [
                          ListTile(
                            leading: const Icon(Icons.image_outlined),
                            title: const Text('Ảnh hoặc video'),
                            onTap: () {
                              Navigator.pop(context);
                              _pickAndSendPhotoOrVideo();
                            },
                          ),
                          ListTile(
                            leading: const Icon(Icons.insert_drive_file_outlined),
                            title: const Text('Tệp tin'),
                            onTap: () {
                              Navigator.pop(context);
                              _pickAndSendFile();
                            },
                          ),
                        ]),
                      ),
                    ),
          ),
          gap,
          IconButton(
            icon: Icon(Icons.emoji_emotions_outlined,
                color: _panel == _Panel.emoji ? Theme.of(context).primaryColor : null),
            iconSize: iconSize,
            padding: buttonPadding,
            constraints: constraints,
            tooltip: 'Emoji',
            onPressed: () => _togglePanel(_Panel.emoji),
          ),
          gap,
          IconButton(
            icon: Icon(Icons.auto_awesome_outlined,
                color: _panel == _Panel.sticker ? Theme.of(context).primaryColor : null),
            iconSize: iconSize,
            padding: buttonPadding,
            constraints: constraints,
            tooltip: 'Nhãn dán',
            onPressed: () => _togglePanel(_Panel.sticker),
          ),
          SizedBox(width: widget.compact ? 4 : 8),
          Expanded(
            child: TextField(
              controller: _controller,
              style: TextStyle(fontSize: widget.compact ? 13 : 14),
              decoration: InputDecoration(
                hintText: widget.compact ? 'Aa' : 'Nhắn tin...',
                isDense: true,
                filled: true,
                fillColor: Colors.grey.shade100,
                contentPadding: EdgeInsets.symmetric(horizontal: 14, vertical: widget.compact ? 8 : 10),
                border: OutlineInputBorder(
                  borderRadius: const BorderRadius.all(Radius.circular(20)),
                  borderSide: BorderSide.none,
                ),
              ),
              onSubmitted: (_) => _sendText(),
            ),
          ),
          SizedBox(width: widget.compact ? 2 : 4),
          ValueListenableBuilder<TextEditingValue>(
            valueListenable: _controller,
            builder: (context, value, _) {
              if (value.text.trim().isNotEmpty) {
                return IconButton(
                  icon: Icon(Icons.send, color: Theme.of(context).primaryColor),
                  iconSize: iconSize,
                  padding: buttonPadding,
                  constraints: constraints,
                  onPressed: _sendText,
                );
              }
              return IconButton(
                icon: const Icon(Icons.mic_none),
                iconSize: iconSize,
                padding: buttonPadding,
                constraints: constraints,
                tooltip: 'Ghi âm',
                onPressed: _uploading ? null : _startRecording,
              );
            },
          ),
        ],
      ),
    );
  }
}
