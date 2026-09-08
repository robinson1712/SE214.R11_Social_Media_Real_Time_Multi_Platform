import 'package:dio/dio.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/page_response.dart';
import '../../core/models/post.dart';

Post _postFromJson(dynamic json) => Post.fromJson(json as Map<String, dynamic>);

class PostRepository {
  final Dio _dio = DioClient.instance;

  Future<Post> create(CreatePostRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/posts',
        data: request.toJson(),
      );
      return ApiResponse.fromJson(response.data!, _postFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Post> getById(String id) async {
    try {
      final response =
          await _dio.get<Map<String, dynamic>>('/api/posts/$id');
      return ApiResponse.fromJson(response.data!, _postFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Post> update(String id, UpdatePostRequest request) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '/api/posts/$id',
        data: request.toJson(),
      );
      return ApiResponse.fromJson(response.data!, _postFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> delete(String id) async {
    try {
      await _dio.delete<void>('/api/posts/$id');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Post> pin(String id) async {
    try {
      final response =
          await _dio.put<Map<String, dynamic>>('/api/posts/$id/pin');
      return ApiResponse.fromJson(response.data!, _postFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Post> unpin(String id) async {
    try {
      final response =
          await _dio.delete<Map<String, dynamic>>('/api/posts/$id/pin');
      return ApiResponse.fromJson(response.data!, _postFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Post> share(String id, ShareRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/posts/$id/share',
        data: request.toJson(),
      );
      return ApiResponse.fromJson(response.data!, _postFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Post> shareReel(String reelId, ShareRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/posts/share-reel/$reelId',
        data: request.toJson(),
      );
      return ApiResponse.fromJson(response.data!, _postFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Post>> byAuthor(String authorId,
      {int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/posts/author/$authorId',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          _postFromJson,
        ),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Post>> byGroup(String groupId,
      {int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/posts/group/$groupId',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          _postFromJson,
        ),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Post>> byPage(String pageId,
      {int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/posts/page/$pageId',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          _postFromJson,
        ),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Post>> search(String q,
      {int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/posts/search',
        queryParameters: {'q': q, 'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          _postFromJson,
        ),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}
