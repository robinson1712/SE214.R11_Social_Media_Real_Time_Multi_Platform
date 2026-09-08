import 'enums.dart';

/// Mirrors chat-service's `Conversation` document.
class Conversation {
  final String id;
  final ConversationType type;
  final List<String> participantIds;
  final String? lastMessagePreview;
  final DateTime? lastMessageAt;
  final DateTime? createdAt;

  const Conversation({
    required this.id,
    required this.type,
    this.participantIds = const [],
    this.lastMessagePreview,
    this.lastMessageAt,
    this.createdAt,
  });

  String otherParticipant(String myId) =>
      participantIds.firstWhere((id) => id != myId, orElse: () => myId);

  factory Conversation.fromJson(Map<String, dynamic> json) => Conversation(
        id: json['id'] as String,
        type: ConversationTypeJson.fromJson(json['type'] as String?),
        participantIds: (json['participantIds'] as List<dynamic>? ?? const [])
            .map((e) => e as String)
            .toList(),
        lastMessagePreview: json['lastMessagePreview'] as String?,
        lastMessageAt: json['lastMessageAt'] == null
            ? null
            : DateTime.tryParse(json['lastMessageAt'] as String),
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );
}

/// Mirrors chat-service's `Message` document — both the REST page shape and
/// the payload pushed over `/user/queue/messages` are identical.
class ChatMessage {
  final String id;
  final String conversationId;
  final String senderId;
  final String? content;
  final String? mediaUrl;
  final String? storyReplyId;
  final String? storyReplyPreviewUrl;
  final DateTime? sentAt;
  final List<String> readBy;
  final bool deleted;

  const ChatMessage({
    required this.id,
    required this.conversationId,
    required this.senderId,
    this.content,
    this.mediaUrl,
    this.storyReplyId,
    this.storyReplyPreviewUrl,
    this.sentAt,
    this.readBy = const [],
    this.deleted = false,
  });

  factory ChatMessage.fromJson(Map<String, dynamic> json) => ChatMessage(
        id: json['id'] as String,
        conversationId: json['conversationId'] as String,
        senderId: json['senderId'] as String,
        content: json['content'] as String?,
        mediaUrl: json['mediaUrl'] as String?,
        storyReplyId: json['storyReplyId'] as String?,
        storyReplyPreviewUrl: json['storyReplyPreviewUrl'] as String?,
        sentAt: json['sentAt'] == null
            ? null
            : DateTime.tryParse(json['sentAt'] as String),
        readBy: (json['readBy'] as List<dynamic>? ?? const [])
            .map((e) => e as String)
            .toList(),
        deleted: json['deleted'] as bool? ?? false,
      );
}

class CreateConversationRequest {
  final List<String>? participantIds;

  const CreateConversationRequest({this.participantIds});

  Map<String, dynamic> toJson() => {'participantIds': participantIds};
}

/// STOMP payload sent to `/app/chat.send`.
class ChatSendRequest {
  final String conversationId;
  final String? content;
  final String? mediaUrl;
  final String? storyReplyId;
  final String? storyReplyPreviewUrl;

  const ChatSendRequest({
    required this.conversationId,
    this.content,
    this.mediaUrl,
    this.storyReplyId,
    this.storyReplyPreviewUrl,
  });

  Map<String, dynamic> toJson() => {
        'conversationId': conversationId,
        'content': content,
        'mediaUrl': mediaUrl,
        'storyReplyId': storyReplyId,
        'storyReplyPreviewUrl': storyReplyPreviewUrl,
      };
}
