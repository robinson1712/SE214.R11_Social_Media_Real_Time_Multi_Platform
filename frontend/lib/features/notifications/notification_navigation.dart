import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/models/app_notification.dart';
import '../../core/models/enums.dart';

/// Where tapping a notification should take the user, based on the
/// `(type, targetType, targetId)` combination the backend attaches —
/// mirrors exactly what `NotificationEventListener` sets on the Kafka
/// event side for each notification type (see notification-service).
void navigateForNotification(BuildContext context, AppNotification n) {
  switch (n.type) {
    case NotificationType.friendRequest:
      context.push('/friends');
      break;
    case NotificationType.comment:
    case NotificationType.tag:
      if (n.targetId != null) context.push('/post/${n.targetId}');
      break;
    case NotificationType.reaction:
      if (n.targetType == 'POST' && n.targetId != null) {
        context.push('/post/${n.targetId}');
      } else if (n.targetType == 'REEL') {
        context.push('/reels');
      }
      break;
    case NotificationType.group:
      if (n.targetId != null) context.push('/groups/${n.targetId}');
      break;
    case NotificationType.match:
      context.push('/dating');
      break;
    case NotificationType.message:
      // Filtered out of the notification list entirely (see chat badge).
      break;
    case NotificationType.follow:
      if (n.targetId != null) context.push('/profile/${n.targetId}');
      break;
  }
}
