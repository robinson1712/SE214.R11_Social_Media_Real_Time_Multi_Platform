import 'enums.dart';

/// Mirrors notification-service's `Notification` document. Named
/// `AppNotification` to avoid clashing with Flutter's own `Notification`
/// widget class.
class AppNotification {
  final String id;
  final String recipientId;
  final String actorId;
  final NotificationType type;
  final String? targetType;
  final String? targetId;
  final String message;
  final bool read;
  final DateTime? createdAt;

  const AppNotification({
    required this.id,
    required this.recipientId,
    required this.actorId,
    required this.type,
    this.targetType,
    this.targetId,
    required this.message,
    this.read = false,
    this.createdAt,
  });

  factory AppNotification.fromJson(Map<String, dynamic> json) =>
      AppNotification(
        id: json['id'] as String,
        recipientId: json['recipientId'] as String,
        actorId: json['actorId'] as String,
        type: NotificationTypeJson.fromJson(json['type'] as String?),
        targetType: json['targetType'] as String?,
        targetId: json['targetId'] as String?,
        message: json['message'] as String? ?? '',
        read: json['read'] as bool? ?? false,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );
}
