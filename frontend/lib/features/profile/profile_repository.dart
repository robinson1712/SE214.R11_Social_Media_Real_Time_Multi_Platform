import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/follow_status.dart';
import '../../core/models/friend_suggestion.dart';
import '../../core/models/friendship.dart';
import '../../core/models/page_response.dart';
import '../../core/models/user_profile.dart';

UserProfile _userProfileFromJson(dynamic json) =>
    UserProfile.fromJson(json as Map<String, dynamic>);

Friendship _friendshipFromJson(dynamic json) =>
    Friendship.fromJson(json as Map<String, dynamic>);

class ProfileRepository {
  final Dio _dio = DioClient.instance;

  Future<UserProfile> getById(String id) async {
    try {
      final response =
          await _dio.get<Map<String, dynamic>>('/api/users/$id');
      return ApiResponse.fromJson(response.data!, _userProfileFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<UserProfile> me() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/api/users/me');
      return ApiResponse.fromJson(response.data!, _userProfileFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<UserProfile> updateMe(UpdateProfileRequest request) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '/api/users/me',
        data: request.toJson(),
      );
      return ApiResponse.fromJson(response.data!, _userProfileFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<UserProfile> updateAvatar(String mediaUrl) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '/api/users/me/avatar',
        data: MediaUrlRequest(mediaUrl: mediaUrl).toJson(),
      );
      return ApiResponse.fromJson(response.data!, _userProfileFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<UserProfile> updateCover(String mediaUrl) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '/api/users/me/cover',
        data: MediaUrlRequest(mediaUrl: mediaUrl).toJson(),
      );
      return ApiResponse.fromJson(response.data!, _userProfileFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<UserProfile>> search(String q,
      {int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/users/search',
        queryParameters: {'q': q, 'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          _userProfileFromJson,
        ),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<FriendshipStatusModel> friendshipStatus(String targetId) async {
    try {
      final response = await _dio
          .get<Map<String, dynamic>>('/api/users/$targetId/friendship-status');
      return ApiResponse.fromJson(
        response.data!,
        (json) =>
            FriendshipStatusModel.fromJson(json as Map<String, dynamic>),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Friendship> sendFriendRequest(String targetId) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/users/$targetId/friend-request',
      );
      return ApiResponse.fromJson(response.data!, _friendshipFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Friendship> acceptFriendRequest(String friendshipId) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '/api/users/friend-requests/$friendshipId/accept',
      );
      return ApiResponse.fromJson(response.data!, _friendshipFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Friendship> declineFriendRequest(String friendshipId) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '/api/users/friend-requests/$friendshipId/decline',
      );
      return ApiResponse.fromJson(response.data!, _friendshipFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> removeFriend(String friendId) async {
    try {
      await _dio.delete<void>('/api/users/friends/$friendId');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<List<String>> friendIds(String userId) async {
    try {
      final response =
          await _dio.get<List<dynamic>>('/api/users/$userId/friend-ids');
      return response.data!.map((e) => e as String).toList();
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<UserProfile>> myFriends(
      {int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/users/me/friends',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          _userProfileFromJson,
        ),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Friendship>> myFriendRequests(
      {int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/users/me/friend-requests',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          _friendshipFromJson,
        ),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> block(String targetId) async {
    try {
      await _dio.post<void>('/api/users/$targetId/block');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> unblock(String targetId) async {
    try {
      await _dio.delete<void>('/api/users/$targetId/block');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> followUser(String targetId) async {
    try {
      await _dio.post<void>('/api/users/$targetId/follow');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> unfollowUser(String targetId) async {
    try {
      await _dio.delete<void>('/api/users/$targetId/follow');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<List<FriendSuggestion>> friendSuggestions({int limit = 10}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/users/me/suggestions',
        queryParameters: {'limit': limit},
      );
      final list = ApiResponse.fromJson(
        response.data!,
        (json) => (json as List<dynamic>)
            .map((e) => FriendSuggestion.fromJson(e as Map<String, dynamic>))
            .toList(),
      ).data;
      return list ?? const [];
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> dismissSuggestion(String targetId) async {
    try {
      await _dio.delete<void>('/api/users/me/suggestions/$targetId');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<FollowStatus> followStatus(String targetId) async {
    try {
      final response =
          await _dio.get<Map<String, dynamic>>('/api/users/$targetId/follow-status');
      return ApiResponse.fromJson(
        response.data!,
        (json) => FollowStatus.fromJson(json as Map<String, dynamic>),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}

final profileRepositoryProvider = Provider((ref) => ProfileRepository());
