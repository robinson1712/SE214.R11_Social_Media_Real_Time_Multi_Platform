import 'package:dio/dio.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/enums.dart';
import '../../core/models/reaction.dart';

Reaction _reactionFromJson(dynamic json) =>
    Reaction.fromJson(json as Map<String, dynamic>);

ReactionSummary _reactionSummaryFromJson(dynamic json) =>
    ReactionSummary.fromJson(json as Map<String, dynamic>);

class ReactionRepository {
  final Dio _dio = DioClient.instance;

  Future<Reaction> upsert(UpsertReactionRequest request) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '/api/reactions',
        data: request.toJson(),
      );
      return ApiResponse.fromJson(response.data!, _reactionFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> delete(TargetType targetType, String targetId) async {
    try {
      await _dio.delete<void>(
        '/api/reactions',
        queryParameters: {
          'targetType': targetType.toJson(),
          'targetId': targetId,
        },
      );
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<ReactionSummary> summary(
      TargetType targetType, String targetId) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/reactions/summary',
        queryParameters: {
          'targetType': targetType.toJson(),
          'targetId': targetId,
        },
      );
      return ApiResponse.fromJson(response.data!, _reactionSummaryFromJson)
          .data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Reaction?> myReaction(TargetType targetType, String targetId) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/reactions/me',
        queryParameters: {
          'targetType': targetType.toJson(),
          'targetId': targetId,
        },
      );
      final raw = response.data!['data'];
      return raw == null ? null : Reaction.fromJson(raw as Map<String, dynamic>);
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}
