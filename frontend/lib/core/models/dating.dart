import 'enums.dart';

/// Mirrors dating-service's `DatingProfile` entity. `id` is the owner's
/// userId itself (not a separate UUID) — the profile is keyed 1:1 per user.
class DatingProfile {
  final String id;
  final Gender gender;
  final DateTime birthDate;
  final String? bio;
  final List<String> interests;
  final int minAgePreference;
  final int maxAgePreference;
  final GenderPreference genderPreference;
  final List<String> photos;
  final bool active;
  final DateTime? createdAt;

  const DatingProfile({
    required this.id,
    required this.gender,
    required this.birthDate,
    this.bio,
    this.interests = const [],
    required this.minAgePreference,
    required this.maxAgePreference,
    this.genderPreference = GenderPreference.any,
    this.photos = const [],
    this.active = true,
    this.createdAt,
  });

  int get age {
    final now = DateTime.now();
    var years = now.year - birthDate.year;
    if (now.month < birthDate.month ||
        (now.month == birthDate.month && now.day < birthDate.day)) {
      years--;
    }
    return years;
  }

  factory DatingProfile.fromJson(Map<String, dynamic> json) => DatingProfile(
        id: json['id'] as String,
        gender: GenderJson.fromJson(json['gender'] as String?) ?? Gender.other,
        birthDate: DateTime.parse(json['birthDate'] as String),
        bio: json['bio'] as String?,
        interests: (json['interests'] as List<dynamic>? ?? const [])
            .map((e) => e as String)
            .toList(),
        minAgePreference: (json['minAgePreference'] as num?)?.toInt() ?? 18,
        maxAgePreference: (json['maxAgePreference'] as num?)?.toInt() ?? 99,
        genderPreference:
            GenderPreferenceJson.fromJson(json['genderPreference'] as String?),
        photos: (json['photos'] as List<dynamic>? ?? const [])
            .map((e) => e as String)
            .toList(),
        active: json['active'] as bool? ?? true,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );
}

class CandidateResponse {
  final DatingProfile profile;
  final int compatibilityScore;

  const CandidateResponse({
    required this.profile,
    required this.compatibilityScore,
  });

  factory CandidateResponse.fromJson(Map<String, dynamic> json) =>
      CandidateResponse(
        profile: DatingProfile.fromJson(json['profile'] as Map<String, dynamic>),
        compatibilityScore: (json['compatibilityScore'] as num?)?.toInt() ?? 0,
      );
}

class Match {
  final String id;
  final String user1Id;
  final String user2Id;
  final DateTime? createdAt;

  const Match({
    required this.id,
    required this.user1Id,
    required this.user2Id,
    this.createdAt,
  });

  String otherUserId(String myId) => user1Id == myId ? user2Id : user1Id;

  factory Match.fromJson(Map<String, dynamic> json) => Match(
        id: json['id'] as String,
        user1Id: json['user1Id'] as String,
        user2Id: json['user2Id'] as String,
        createdAt: json['createdAt'] == null
            ? null
            : DateTime.tryParse(json['createdAt'] as String),
      );
}

class UpsertProfileRequest {
  final Gender gender;
  final DateTime birthDate;
  final String? bio;
  final List<String>? interests;
  final int minAgePreference;
  final int maxAgePreference;
  final GenderPreference? genderPreference;
  final List<String>? photos;

  const UpsertProfileRequest({
    required this.gender,
    required this.birthDate,
    this.bio,
    this.interests,
    required this.minAgePreference,
    required this.maxAgePreference,
    this.genderPreference,
    this.photos,
  });

  Map<String, dynamic> toJson() => {
        'gender': gender.toJson(),
        'birthDate':
            '${birthDate.year.toString().padLeft(4, '0')}-${birthDate.month.toString().padLeft(2, '0')}-${birthDate.day.toString().padLeft(2, '0')}',
        'bio': bio,
        'interests': interests,
        'minAgePreference': minAgePreference,
        'maxAgePreference': maxAgePreference,
        if (genderPreference != null) 'genderPreference': genderPreference!.toJson(),
        'photos': photos,
      };
}

class SwipeRequest {
  final String targetId;
  final SwipeAction action;

  const SwipeRequest({required this.targetId, required this.action});

  Map<String, dynamic> toJson() => {
        'targetId': targetId,
        'action': action.toJson(),
      };
}

class SwipeResponse {
  final bool swiped;
  final bool matched;

  const SwipeResponse({required this.swiped, required this.matched});

  factory SwipeResponse.fromJson(Map<String, dynamic> json) => SwipeResponse(
        swiped: json['swiped'] as bool? ?? false,
        matched: json['matched'] as bool? ?? false,
      );
}
