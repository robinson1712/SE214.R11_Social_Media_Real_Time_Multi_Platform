/// Shared enums mirroring the Java enums in common-lib / per-service entities.
/// Backend sends/expects the plain uppercase enum name as a JSON string
/// (Jackson's default `@Enumerated(EnumType.STRING)` / record serialization).

enum Privacy { public, friends, custom, private }

extension PrivacyJson on Privacy {
  String toJson() => name.toUpperCase();

  static Privacy fromJson(String? value) {
    switch (value) {
      case 'FRIENDS':
        return Privacy.friends;
      case 'CUSTOM':
        return Privacy.custom;
      case 'PRIVATE':
        return Privacy.private;
      case 'PUBLIC':
      default:
        return Privacy.public;
    }
  }

  String get label {
    switch (this) {
      case Privacy.public:
        return 'Công khai';
      case Privacy.friends:
        return 'Bạn bè';
      case Privacy.custom:
        return 'Tuỳ chỉnh';
      case Privacy.private:
        return 'Chỉ mình tôi';
    }
  }
}

enum ReactionType { like, love, haha, wow, sad, angry }

extension ReactionTypeJson on ReactionType {
  String toJson() => name.toUpperCase();

  static ReactionType? fromJson(String? value) {
    if (value == null) return null;
    for (final t in ReactionType.values) {
      if (t.name.toUpperCase() == value) return t;
    }
    return null;
  }

  String get emoji {
    switch (this) {
      case ReactionType.like:
        return '👍';
      case ReactionType.love:
        return '❤️';
      case ReactionType.haha:
        return '😆';
      case ReactionType.wow:
        return '😮';
      case ReactionType.sad:
        return '😢';
      case ReactionType.angry:
        return '😡';
    }
  }
}

enum TargetType { post, comment, reel, story }

extension TargetTypeJson on TargetType {
  String toJson() => name.toUpperCase();

  static TargetType fromJson(String? value) {
    for (final t in TargetType.values) {
      if (t.name.toUpperCase() == value) return t;
    }
    return TargetType.post;
  }
}

enum Gender { male, female, other }

extension GenderJson on Gender {
  String toJson() => name.toUpperCase();

  static Gender? fromJson(String? value) {
    switch (value) {
      case 'MALE':
        return Gender.male;
      case 'FEMALE':
        return Gender.female;
      case 'OTHER':
        return Gender.other;
      default:
        return null;
    }
  }
}

enum FriendshipStatus { pending, accepted, declined }

extension FriendshipStatusJson on FriendshipStatus {
  static FriendshipStatus fromJson(String? value) {
    switch (value) {
      case 'ACCEPTED':
        return FriendshipStatus.accepted;
      case 'DECLINED':
        return FriendshipStatus.declined;
      case 'PENDING':
      default:
        return FriendshipStatus.pending;
    }
  }
}

/// The caller's relationship to a specific other user — mirrors user-service's
/// `RelationshipStatus`. Finer-grained than [FriendshipStatus] alone, which
/// can't tell "I sent this pending request" apart from "they sent it to me".
enum RelationshipStatus { none, pendingSent, pendingReceived, friends }

extension RelationshipStatusJson on RelationshipStatus {
  static RelationshipStatus fromJson(String? value) {
    switch (value) {
      case 'PENDING_SENT':
        return RelationshipStatus.pendingSent;
      case 'PENDING_RECEIVED':
        return RelationshipStatus.pendingReceived;
      case 'FRIENDS':
        return RelationshipStatus.friends;
      case 'NONE':
      default:
        return RelationshipStatus.none;
    }
  }
}

/// Matches media-service's MediaPurpose enum exactly (upload multipart field).
enum MediaPurpose { avatar, cover, post, story, reel, chat, page, group }

extension MediaPurposeJson on MediaPurpose {
  String toJson() => name.toUpperCase();
}

// ---------------------------------------------------------------------------
// group-service
// ---------------------------------------------------------------------------

enum GroupPrivacy { public, private }

extension GroupPrivacyJson on GroupPrivacy {
  String toJson() => name.toUpperCase();

  static GroupPrivacy fromJson(String? value) =>
      value == 'PRIVATE' ? GroupPrivacy.private : GroupPrivacy.public;

  String get label => this == GroupPrivacy.public ? 'Công khai' : 'Riêng tư';
}

enum MemberRole { admin, moderator, member }

extension MemberRoleJson on MemberRole {
  String toJson() => name.toUpperCase();

  static MemberRole fromJson(String? value) {
    switch (value) {
      case 'ADMIN':
        return MemberRole.admin;
      case 'MODERATOR':
        return MemberRole.moderator;
      case 'MEMBER':
      default:
        return MemberRole.member;
    }
  }

  String get label {
    switch (this) {
      case MemberRole.admin:
        return 'Quản trị viên';
      case MemberRole.moderator:
        return 'Người kiểm duyệt';
      case MemberRole.member:
        return 'Thành viên';
    }
  }
}

enum MemberStatus { pending, approved }

extension MemberStatusJson on MemberStatus {
  static MemberStatus fromJson(String? value) =>
      value == 'APPROVED' ? MemberStatus.approved : MemberStatus.pending;
}

// ---------------------------------------------------------------------------
// fanpage-service
// ---------------------------------------------------------------------------

enum FanpageAdminRole { owner, admin, editor }

extension FanpageAdminRoleJson on FanpageAdminRole {
  String toJson() => name.toUpperCase();

  static FanpageAdminRole fromJson(String? value) {
    switch (value) {
      case 'OWNER':
        return FanpageAdminRole.owner;
      case 'EDITOR':
        return FanpageAdminRole.editor;
      case 'ADMIN':
      default:
        return FanpageAdminRole.admin;
    }
  }

  String get label {
    switch (this) {
      case FanpageAdminRole.owner:
        return 'Chủ trang';
      case FanpageAdminRole.admin:
        return 'Quản trị viên';
      case FanpageAdminRole.editor:
        return 'Biên tập viên';
    }
  }
}

// ---------------------------------------------------------------------------
// story-service
// ---------------------------------------------------------------------------

enum StoryMediaType { image, video }

extension StoryMediaTypeJson on StoryMediaType {
  String toJson() => name.toUpperCase();

  static StoryMediaType fromJson(String? value) =>
      value == 'VIDEO' ? StoryMediaType.video : StoryMediaType.image;
}

// ---------------------------------------------------------------------------
// dating-service
// ---------------------------------------------------------------------------

enum GenderPreference { any, male, female, other }

extension GenderPreferenceJson on GenderPreference {
  String toJson() => name.toUpperCase();

  static GenderPreference fromJson(String? value) {
    switch (value) {
      case 'MALE':
        return GenderPreference.male;
      case 'FEMALE':
        return GenderPreference.female;
      case 'OTHER':
        return GenderPreference.other;
      case 'ANY':
      default:
        return GenderPreference.any;
    }
  }

  String get label {
    switch (this) {
      case GenderPreference.any:
        return 'Bất kỳ';
      case GenderPreference.male:
        return 'Nam';
      case GenderPreference.female:
        return 'Nữ';
      case GenderPreference.other:
        return 'Khác';
    }
  }
}

enum SwipeAction { like, pass }

extension SwipeActionJson on SwipeAction {
  String toJson() => name.toUpperCase();
}

// ---------------------------------------------------------------------------
// chat-service
// ---------------------------------------------------------------------------

enum ConversationType { private_, group }

extension ConversationTypeJson on ConversationType {
  static ConversationType fromJson(String? value) =>
      value == 'GROUP' ? ConversationType.group : ConversationType.private_;
}

// ---------------------------------------------------------------------------
// notification-service
// ---------------------------------------------------------------------------

enum NotificationType {
  friendRequest,
  comment,
  reaction,
  group,
  match,
  message,
  tag,
  follow,
}

extension NotificationTypeJson on NotificationType {
  static NotificationType fromJson(String? value) {
    switch (value) {
      case 'FRIEND_REQUEST':
        return NotificationType.friendRequest;
      case 'COMMENT':
        return NotificationType.comment;
      case 'REACTION':
        return NotificationType.reaction;
      case 'GROUP':
        return NotificationType.group;
      case 'MATCH':
        return NotificationType.match;
      case 'MESSAGE':
        return NotificationType.message;
      case 'TAG':
        return NotificationType.tag;
      case 'FOLLOW':
      default:
        return NotificationType.follow;
    }
  }

  String get icon {
    switch (this) {
      case NotificationType.friendRequest:
        return '👤';
      case NotificationType.comment:
        return '💬';
      case NotificationType.reaction:
        return '👍';
      case NotificationType.group:
        return '👥';
      case NotificationType.match:
        return '💘';
      case NotificationType.message:
        return '✉️';
      case NotificationType.tag:
        return '🏷️';
      case NotificationType.follow:
        return '➕';
    }
  }
}

// ---------------------------------------------------------------------------
// moderation-service
// ---------------------------------------------------------------------------

enum ReportTargetType { post, comment, reel, story, user, group, fanpage }

extension ReportTargetTypeJson on ReportTargetType {
  String toJson() => name.toUpperCase();

  static ReportTargetType fromJson(String? value) {
    for (final t in ReportTargetType.values) {
      if (t.name.toUpperCase() == value) return t;
    }
    return ReportTargetType.post;
  }
}

enum ReportReason {
  spam,
  harassment,
  hateSpeech,
  nudity,
  violence,
  misinformation,
  other,
}

extension ReportReasonJson on ReportReason {
  String toJson() {
    switch (this) {
      case ReportReason.hateSpeech:
        return 'HATE_SPEECH';
      case ReportReason.misinformation:
        return 'MISINFORMATION';
      default:
        return name.toUpperCase();
    }
  }

  static ReportReason fromJson(String? value) {
    switch (value) {
      case 'HARASSMENT':
        return ReportReason.harassment;
      case 'HATE_SPEECH':
        return ReportReason.hateSpeech;
      case 'NUDITY':
        return ReportReason.nudity;
      case 'VIOLENCE':
        return ReportReason.violence;
      case 'MISINFORMATION':
        return ReportReason.misinformation;
      case 'OTHER':
        return ReportReason.other;
      case 'SPAM':
      default:
        return ReportReason.spam;
    }
  }

  String get label {
    switch (this) {
      case ReportReason.spam:
        return 'Spam';
      case ReportReason.harassment:
        return 'Quấy rối';
      case ReportReason.hateSpeech:
        return 'Phát ngôn thù ghét';
      case ReportReason.nudity:
        return 'Nội dung khoả thân';
      case ReportReason.violence:
        return 'Bạo lực';
      case ReportReason.misinformation:
        return 'Thông tin sai lệch';
      case ReportReason.other:
        return 'Khác';
    }
  }
}

enum ReportStatus { pending, dismissed, actionTaken }

extension ReportStatusJson on ReportStatus {
  String toJson() => this == ReportStatus.actionTaken ? 'ACTION_TAKEN' : name.toUpperCase();

  static ReportStatus fromJson(String? value) {
    switch (value) {
      case 'DISMISSED':
        return ReportStatus.dismissed;
      case 'ACTION_TAKEN':
        return ReportStatus.actionTaken;
      case 'PENDING':
      default:
        return ReportStatus.pending;
    }
  }

  String get label {
    switch (this) {
      case ReportStatus.pending:
        return 'Chờ xử lý';
      case ReportStatus.dismissed:
        return 'Đã bỏ qua';
      case ReportStatus.actionTaken:
        return 'Đã xử lý';
    }
  }
}

enum ModerationAction { dismiss, removeContent }

extension ModerationActionJson on ModerationAction {
  String toJson() =>
      this == ModerationAction.removeContent ? 'REMOVE_CONTENT' : 'DISMISS';
}
