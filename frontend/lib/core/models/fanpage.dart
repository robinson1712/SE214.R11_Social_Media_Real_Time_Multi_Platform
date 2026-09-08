import 'enums.dart';

/// Mirrors fanpage-service's `Fanpage` entity.
class Fanpage {
  final String id;
  final String name;
  final String? category;
  final String? description;
  final String? avatarUrl;
  final String? coverUrl;
  final String ownerId;
  final int followerCount;
  final DateTime? createdAt;

  const Fanpage({
    required this.id,
    required this.name,
    this.category,
    this.description,
    this.avatarUrl,
    this.coverUrl,
    required this.ownerId,
    this.followerCount = 0,
    this.createdAt,
  });

  factory Fanpage.fromJson(Map<String, dynamic> json) => Fanpage(
        id: json['id'] as String,
        name: json['name'] as String? ?? '',
        category: json['category'] as String?,
        description: json['description'] as String?,
        avatarUrl: json['avatarUrl'] as String?,
        coverUrl: json['coverUrl'] as String?,
        ownerId: json['ownerId'] as String,
        followerCount: (json['followerCount'] as num?)?.toInt() ?? 0,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );
}

class PageFollower {
  final String id;
  final String pageId;
  final String userId;
  final DateTime? createdAt;

  const PageFollower({
    required this.id,
    required this.pageId,
    required this.userId,
    this.createdAt,
  });

  factory PageFollower.fromJson(Map<String, dynamic> json) => PageFollower(
        id: json['id'] as String,
        pageId: json['pageId'] as String,
        userId: json['userId'] as String,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );
}

class PageAdmin {
  final String id;
  final String pageId;
  final String userId;
  final FanpageAdminRole role;
  final DateTime? addedAt;

  const PageAdmin({
    required this.id,
    required this.pageId,
    required this.userId,
    required this.role,
    this.addedAt,
  });

  factory PageAdmin.fromJson(Map<String, dynamic> json) => PageAdmin(
        id: json['id'] as String,
        pageId: json['pageId'] as String,
        userId: json['userId'] as String,
        role: FanpageAdminRoleJson.fromJson(json['role'] as String?),
        addedAt: json['addedAt'] == null
            ? null
            : DateTime.tryParse(json['addedAt'] as String),
      );
}

class CreateFanpageRequest {
  final String name;
  final String? category;
  final String? description;

  const CreateFanpageRequest({
    required this.name,
    this.category,
    this.description,
  });

  Map<String, dynamic> toJson() => {
        'name': name,
        'category': category,
        'description': description,
      };
}

class AddAdminRequest {
  final String userId;
  final FanpageAdminRole role;

  const AddAdminRequest({required this.userId, required this.role});

  Map<String, dynamic> toJson() => {'userId': userId, 'role': role.toJson()};
}
