import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/fanpage.dart';
import '../../core/models/page_response.dart';

Fanpage _fanpageFromJson(dynamic json) => Fanpage.fromJson(json as Map<String, dynamic>);

PageFollower _followerFromJson(dynamic json) =>
    PageFollower.fromJson(json as Map<String, dynamic>);

class FanpageRepository {
  final Dio _dio = DioClient.instance;

  Future<Fanpage> create(CreateFanpageRequest request) async {
    try {
      final response =
          await _dio.post<Map<String, dynamic>>('/api/pages', data: request.toJson());
      return ApiResponse.fromJson(response.data!, _fanpageFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Fanpage> getById(String id) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/api/pages/$id');
      return ApiResponse.fromJson(response.data!, _fanpageFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Fanpage>> list({String? name, int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/pages',
        queryParameters: {if (name != null && name.isNotEmpty) 'name': name, 'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _fanpageFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Fanpage>> myManaged({int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/pages/me/managed',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _fanpageFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Fanpage>> myFollowed({int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/pages/me/followed',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _fanpageFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> follow(String pageId) async {
    try {
      await _dio.post<void>('/api/pages/$pageId/follow');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> unfollow(String pageId) async {
    try {
      await _dio.delete<void>('/api/pages/$pageId/follow');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<PageFollower>> followers(String pageId, {int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/pages/$pageId/followers',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _followerFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}

final fanpageRepositoryProvider = Provider((ref) => FanpageRepository());
