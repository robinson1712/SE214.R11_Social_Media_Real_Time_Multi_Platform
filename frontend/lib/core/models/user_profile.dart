import 'enums.dart';

/// Mirrors user-service's `UserProfileResponse`.
class UserProfile {
  final String id;
  final String fullName;
  final String? avatarUrl;
  final String? coverUrl;
  final String? bio;
  final DateTime? dob;
  final Gender? gender;
  final String? location;
  final String? workplace;
  final bool readReceiptsEnabled;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  const UserProfile({
    required this.id,
    required this.fullName,
    this.avatarUrl,
    this.coverUrl,
    this.bio,
    this.dob,
    this.gender,
    this.location,
    this.workplace,
    this.readReceiptsEnabled = true,
    this.createdAt,
    this.updatedAt,
  });

  factory UserProfile.fromJson(Map<String, dynamic> json) => UserProfile(
        id: json['id'] as String,
        fullName: json['fullName'] as String? ?? '',
        avatarUrl: json['avatarUrl'] as String?,
        coverUrl: json['coverUrl'] as String?,
        bio: json['bio'] as String?,
        dob: json['dob'] == null
            ? null
            : DateTime.tryParse(json['dob'] as String),
        gender: GenderJson.fromJson(json['gender'] as String?),
        location: json['location'] as String?,
        workplace: json['workplace'] as String?,
        readReceiptsEnabled: json['readReceiptsEnabled'] as bool? ?? true,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
        updatedAt: json['updatedAt'] == null
            ? null
            : DateTime.tryParse(json['updatedAt'] as String),
      );
}

class UpdateProfileRequest {
  final String? fullName;
  final String? bio;
  final DateTime? dob;
  final Gender? gender;
  final String? location;
  final String? workplace;
  final bool? readReceiptsEnabled;

  const UpdateProfileRequest({
    this.fullName,
    this.bio,
    this.dob,
    this.gender,
    this.location,
    this.workplace,
    this.readReceiptsEnabled,
  });

  Map<String, dynamic> toJson() => {
        'fullName': fullName,
        'bio': bio,
        if (dob != null)
          'dob':
              '${dob!.year.toString().padLeft(4, '0')}-${dob!.month.toString().padLeft(2, '0')}-${dob!.day.toString().padLeft(2, '0')}',
        if (gender != null) 'gender': gender!.toJson(),
        'location': location,
        'workplace': workplace,
        if (readReceiptsEnabled != null) 'readReceiptsEnabled': readReceiptsEnabled,
      };
}

class MediaUrlRequest {
  final String mediaUrl;

  const MediaUrlRequest({required this.mediaUrl});

  Map<String, dynamic> toJson() => {'mediaUrl': mediaUrl};
}
