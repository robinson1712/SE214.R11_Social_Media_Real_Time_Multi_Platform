import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/enums.dart';
import '../../core/models/group.dart';
import '../../core/models/page_response.dart';

Group _groupFromJson(dynamic json) => Group.fromJson(json as Map<String, dynamic>);

GroupMember _memberFromJson(dynamic json) =>
    GroupMember.fromJson(json as Map<String, dynamic>);

class GroupRepository {
  final Dio _dio = DioClient.instance;

  Future<Group> create(CreateGroupRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/groups',
        data: request.toJson(),
      );
      return ApiResponse.fromJson(response.data!, _groupFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Group> getById(String id) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/api/groups/$id');
      return ApiResponse.fromJson(response.data!, _groupFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Group>> list({String? name, int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/groups',
        queryParameters: {if (name != null && name.isNotEmpty) 'name': name, 'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _groupFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Group>> myGroups({int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/groups/me',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _groupFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<GroupMember> join(String groupId) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>('/api/groups/$groupId/join');
      return ApiResponse.fromJson(response.data!, _memberFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> leave(String groupId) async {
    try {
      await _dio.delete<void>('/api/groups/$groupId/leave');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<GroupMember> approve(String groupId, String userId) async {
    try {
      final response =
          await _dio.put<Map<String, dynamic>>('/api/groups/$groupId/members/$userId/approve');
      return ApiResponse.fromJson(response.data!, _memberFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> kick(String groupId, String userId) async {
    try {
      await _dio.delete<void>('/api/groups/$groupId/members/$userId');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<GroupMember> changeRole(String groupId, String userId, MemberRole role) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '/api/groups/$groupId/members/$userId/role',
        data: ChangeRoleRequest(role: role).toJson(),
      );
      return ApiResponse.fromJson(response.data!, _memberFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<GroupMember>> members(String groupId, {int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/groups/$groupId/members',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _memberFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}

final groupRepositoryProvider = Provider((ref) => GroupRepository());
