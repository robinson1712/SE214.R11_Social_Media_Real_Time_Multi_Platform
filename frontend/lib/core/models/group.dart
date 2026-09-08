import 'enums.dart';

/// Mirrors group-service's `Group` entity.
class Group {
  final String id;
  final String name;
  final String? description;
  final String? avatarUrl;
  final String? coverUrl;
  final GroupPrivacy privacy;
  final String ownerId;
  final int memberCount;
  final DateTime? createdAt;

  const Group({
    required this.id,
    required this.name,
    this.description,
    this.avatarUrl,
    this.coverUrl,
    required this.privacy,
    required this.ownerId,
    this.memberCount = 1,
    this.createdAt,
  });

  factory Group.fromJson(Map<String, dynamic> json) => Group(
        id: json['id'] as String,
        name: json['name'] as String? ?? '',
        description: json['description'] as String?,
        avatarUrl: json['avatarUrl'] as String?,
        coverUrl: json['coverUrl'] as String?,
        privacy: GroupPrivacyJson.fromJson(json['privacy'] as String?),
        ownerId: json['ownerId'] as String,
        memberCount: (json['memberCount'] as num?)?.toInt() ?? 1,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );
}

/// Mirrors group-service's `GroupMember` entity.
class GroupMember {
  final String id;
  final String groupId;
  final String userId;
  final MemberRole role;
  final MemberStatus status;
  final DateTime? joinedAt;

  const GroupMember({
    required this.id,
    required this.groupId,
    required this.userId,
    required this.role,
    required this.status,
    this.joinedAt,
  });

  factory GroupMember.fromJson(Map<String, dynamic> json) => GroupMember(
        id: json['id'] as String,
        groupId: json['groupId'] as String,
        userId: json['userId'] as String,
        role: MemberRoleJson.fromJson(json['role'] as String?),
        status: MemberStatusJson.fromJson(json['status'] as String?),
        joinedAt: json['joinedAt'] == null
            ? null
            : DateTime.tryParse(json['joinedAt'] as String),
      );
}

class CreateGroupRequest {
  final String name;
  final String? description;
  final GroupPrivacy privacy;

  const CreateGroupRequest({
    required this.name,
    this.description,
    required this.privacy,
  });

  Map<String, dynamic> toJson() => {
        'name': name,
        'description': description,
        'privacy': privacy.toJson(),
      };
}

class ChangeRoleRequest {
  final MemberRole role;

  const ChangeRoleRequest({required this.role});

  Map<String, dynamic> toJson() => {'role': role.toJson()};
}
