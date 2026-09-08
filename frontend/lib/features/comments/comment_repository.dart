import 'package:dio/dio.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/comment.dart';
import '../../core/models/enums.dart';
import '../../core/models/page_response.dart';

Comment _commentFromJson(dynamic json) =>
    Comment.fromJson(json as Map<String, dynamic>);

class CommentRepository {
  final Dio _dio = DioClient.instance;

  Future<Comment> create(CreateCommentRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/comments',
        data: request.toJson(),
      );
      return ApiResponse.fromJson(response.data!, _commentFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Comment>> topLevel(TargetType targetType, String targetId,
      {int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/comments',
        queryParameters: {
          'targetType': targetType.toJson(),
          'targetId': targetId,
          'page': page,
          'size': size,
        },
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          _commentFromJson,
        ),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Comment>> replies(String commentId,
      {int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/comments/$commentId/replies',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          _commentFromJson,
        ),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> delete(String id) async {
    try {
      await _dio.delete<void>('/api/comments/$id');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}
