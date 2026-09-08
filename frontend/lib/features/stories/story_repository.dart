import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/story.dart';

Story _storyFromJson(dynamic json) => Story.fromJson(json as Map<String, dynamic>);

class StoryRepository {
  final Dio _dio = DioClient.instance;

  Future<Story> create(CreateStoryRequest request) async {
    try {
      final response =
          await _dio.post<Map<String, dynamic>>('/api/stories', data: request.toJson());
      return ApiResponse.fromJson(response.data!, _storyFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<List<Story>> feed() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/api/stories/feed');
      final list = ApiResponse.fromJson(
        response.data!,
        (json) => (json as List<dynamic>)
            .map((e) => Story.fromJson(e as Map<String, dynamic>))
            .toList(),
      ).data;
      return list ?? const [];
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<List<Story>> byAuthor(String authorId) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/api/stories/author/$authorId');
      final list = ApiResponse.fromJson(
        response.data!,
        (json) => (json as List<dynamic>)
            .map((e) => Story.fromJson(e as Map<String, dynamic>))
            .toList(),
      ).data;
      return list ?? const [];
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Story> view(String id) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>('/api/stories/$id/view');
      return ApiResponse.fromJson(response.data!, _storyFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> delete(String id) async {
    try {
      await _dio.delete<void>('/api/stories/$id');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}

final storyRepositoryProvider = Provider((ref) => StoryRepository());
