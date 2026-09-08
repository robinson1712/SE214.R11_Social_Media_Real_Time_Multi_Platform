import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/models/chat.dart';
import 'chat_message_kind.dart';

/// Renders one message's body — text, a sticker (big emoji, no bubble), or
/// an image/video/audio/file attachment — shared between the full
/// [ChatThreadPanel] and the small floating chat windows so attachment
/// support doesn't have to be built (and kept in sync) twice.
class ChatMessageBody extends StatelessWidget {
  final ChatMessage message;
  final bool mine;
  final bool compact;

  const ChatMessageBody({
    super.key,
    required this.message,
    required this.mine,
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    if (message.deleted) {
      return Text(
        '(tin nhắn đã xoá)',
        style: TextStyle(
          color: mine ? Colors.white : Colors.black87,
          fontStyle: FontStyle.italic,
          fontSize: compact ? 13 : 14,
        ),
      );
    }

    final content = message.content;
    if (isStickerMessage(content)) {
      return Text(stickerEmoji(content!), style: TextStyle(fontSize: compact ? 40 : 56));
    }

    final kind = attachmentKindFor(message.mediaUrl);
    if (kind == null) {
      return Text(
        content ?? '',
        style: TextStyle(color: mine ? Colors.white : Colors.black87, fontSize: compact ? 13 : 14),
      );
    }

    final textColor = mine ? Colors.white : Colors.black87;
    Widget attachment;
    switch (kind) {
      case ChatAttachmentKind.image:
        attachment = ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: Image.network(
            message.mediaUrl!,
            width: compact ? 160 : 260,
            fit: BoxFit.cover,
            errorBuilder: (context, _, __) => Container(
              width: compact ? 160 : 260,
              height: compact ? 120 : 180,
              color: Colors.grey.shade300,
              child: const Icon(Icons.broken_image),
            ),
          ),
        );
        break;
      case ChatAttachmentKind.video:
        attachment = _VideoAttachment(url: message.mediaUrl!, compact: compact);
        break;
      case ChatAttachmentKind.audio:
        attachment = _AudioAttachment(url: message.mediaUrl!, mine: mine, compact: compact);
        break;
      case ChatAttachmentKind.file:
        attachment = _FileAttachment(url: message.mediaUrl!, mine: mine, compact: compact);
        break;
    }

    if (content == null || content.isEmpty) return attachment;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        attachment,
        const SizedBox(height: 6),
        Text(content, style: TextStyle(color: textColor, fontSize: compact ? 13 : 14)),
      ],
    );
  }
}

/// Tapping opens the raw file URL in a new tab — this app has no in-page
/// video player chrome beyond play/pause, and a browser's native video
/// controls (via the `<video>` element it opens as) already give scrubbing,
/// volume and fullscreen for free.
class _VideoAttachment extends StatelessWidget {
  final String url;
  final bool compact;

  const _VideoAttachment({required this.url, required this.compact});

  @override
  Widget build(BuildContext context) {
    final size = compact ? 160.0 : 260.0;
    return InkWell(
      onTap: () => _openInNewTab(url),
      borderRadius: BorderRadius.circular(8),
      child: Container(
        width: size,
        height: size * 0.65,
        decoration: BoxDecoration(color: Colors.black87, borderRadius: BorderRadius.circular(8)),
        child: const Center(
          child: Icon(Icons.play_circle_fill, color: Colors.white, size: 40),
        ),
      ),
    );
  }
}

class _AudioAttachment extends StatefulWidget {
  final String url;
  final bool mine;
  final bool compact;

  const _AudioAttachment({required this.url, required this.mine, required this.compact});

  @override
  State<_AudioAttachment> createState() => _AudioAttachmentState();
}

class _AudioAttachmentState extends State<_AudioAttachment> {
  final _player = AudioPlayer();
  bool _playing = false;

  @override
  void initState() {
    super.initState();
    _player.onPlayerStateChanged.listen((state) {
      if (!mounted) return;
      setState(() => _playing = state == PlayerState.playing);
    });
    _player.onPlayerComplete.listen((_) {
      if (!mounted) return;
      setState(() => _playing = false);
    });
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  Future<void> _toggle() async {
    if (_playing) {
      await _player.pause();
    } else {
      await _player.play(UrlSource(widget.url));
    }
  }

  @override
  Widget build(BuildContext context) {
    final color = widget.mine ? Colors.white : Theme.of(context).primaryColor;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        InkWell(
          onTap: _toggle,
          borderRadius: BorderRadius.circular(20),
          child: Icon(_playing ? Icons.pause_circle_filled : Icons.play_circle_fill,
              color: color, size: widget.compact ? 30 : 36),
        ),
        const SizedBox(width: 8),
        Icon(Icons.graphic_eq, size: widget.compact ? 16 : 18, color: color),
        const SizedBox(width: 4),
        Text('Tin nhắn thoại',
            style: TextStyle(
                fontSize: widget.compact ? 12 : 13,
                color: widget.mine ? Colors.white70 : Colors.grey.shade700)),
      ],
    );
  }
}

class _FileAttachment extends StatelessWidget {
  final String url;
  final bool mine;
  final bool compact;

  const _FileAttachment({required this.url, required this.mine, required this.compact});

  @override
  Widget build(BuildContext context) {
    final color = mine ? Colors.white : Colors.black87;
    return InkWell(
      onTap: () => _openInNewTab(url),
      borderRadius: BorderRadius.circular(8),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.insert_drive_file, color: color, size: compact ? 22 : 26),
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              attachmentDisplayName(url),
              overflow: TextOverflow.ellipsis,
              style: TextStyle(color: color, fontSize: compact ? 12 : 13, decoration: TextDecoration.underline),
            ),
          ),
        ],
      ),
    );
  }
}

void _openInNewTab(String url) {
  final uri = Uri.tryParse(url);
  if (uri == null) return;
  launchUrl(uri, webOnlyWindowName: '_blank');
}
