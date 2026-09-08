import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/page_response.dart';
import '../../core/models/reel.dart';

Reel _reelFromJson(dynamic json) => Reel.fromJson(json as Map<String, dynamic>);

class ReelRepository {
  final Dio _dio = DioClient.instance;

  Future<Reel> create(CreateReelRequest request) async {
    try {
      final response =
          await _dio.post<Map<String, dynamic>>('/api/reels', data: request.toJson());
      return ApiResponse.fromJson(response.data!, _reelFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Reel> getById(String id) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/api/reels/$id');
      return ApiResponse.fromJson(response.data!, _reelFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Reel> view(String id) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>('/api/reels/$id/view');
      return ApiResponse.fromJson(response.data!, _reelFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Reel>> feed({int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/reels/feed',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _reelFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Reel>> byAuthor(String authorId, {int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/reels/author/$authorId',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _reelFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> delete(String id) async {
    try {
      await _dio.delete<void>('/api/reels/$id');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}

final reelRepositoryProvider = Provider((ref) => ReelRepository());
