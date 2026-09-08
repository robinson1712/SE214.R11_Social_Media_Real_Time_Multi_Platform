/// Helpers for turning a [ChatMessage]'s raw `content`/`mediaUrl` fields into
/// something the UI can render as more than plain text — the backend only
/// stores those two strings (see chat-service's `Message` document), so
/// "what kind of message is this" is entirely inferred client-side.
library;

enum ChatAttachmentKind { image, video, audio, file }

const _imageExts = {'jpg', 'jpeg', 'png', 'gif', 'webp'};
const _videoExts = {'mp4', 'webm', 'mov', 'qt'};
// `.weba` is the extension this app's own voice recorder uploads under (see
// chat_composer_bar.dart) — deliberately NOT `.webm`, which is reserved for
// real video attachments above; both are physically the same container
// format, so the extension is the only client-side signal that tells them
// apart (the upload's actual Content-Type header is set correctly either
// way — see MediaRepository.uploadBytes — this is purely a rendering hint).
const _audioExts = {'weba', 'mp3', 'wav', 'm4a', 'ogg', 'oga', 'opus'};

/// Classifies an attachment by the file extension in its URL. Returns null
/// for a message with no attachment, or [ChatAttachmentKind.file] for any
/// attachment type that isn't specifically an image/video/audio.
ChatAttachmentKind? attachmentKindFor(String? mediaUrl) {
  if (mediaUrl == null || mediaUrl.isEmpty) return null;
  final path = Uri.tryParse(mediaUrl)?.path ?? mediaUrl;
  final dot = path.lastIndexOf('.');
  final ext = dot == -1 ? '' : path.substring(dot + 1).toLowerCase();
  if (_imageExts.contains(ext)) return ChatAttachmentKind.image;
  if (_videoExts.contains(ext)) return ChatAttachmentKind.video;
  if (_audioExts.contains(ext)) return ChatAttachmentKind.audio;
  return ChatAttachmentKind.file;
}

/// Best-effort original filename for a file-attachment bubble, recovered
/// from the object key media-service generates
/// (`purpose/ownerId/uuid-originalFilename`, see MediaService.upload) —
/// falls back to the last URL segment if the shape doesn't match.
String attachmentDisplayName(String mediaUrl) {
  final path = Uri.tryParse(mediaUrl)?.path ?? mediaUrl;
  final last = path.split('/').last;
  final dashIndex = last.indexOf('-');
  // UUIDs are 36 chars; anything past "uuid-" is the original filename.
  if (dashIndex > 0 && last.length > 37 && last.substring(0, dashIndex).length <= 36) {
    final afterUuid = last.substring(37);
    if (afterUuid.startsWith('-')) return afterUuid.substring(1);
  }
  return last;
}

/// Sentinel prefix marking a message sent from the sticker picker (a single
/// large emoji, rendered without a bubble — see chat_message_bubble.dart).
/// Uses a Unicode Private Use Area codepoint (``) so it can never
/// collide with anything a real keyboard or paste could produce, unlike
/// e.g. a plain-text prefix — no heuristic guessing at "is this short text
/// a sticker".
const stickerMarker = '';

bool isStickerMessage(String? content) =>
    content != null && content.startsWith(stickerMarker);

String stickerEmoji(String content) => content.substring(stickerMarker.length);
