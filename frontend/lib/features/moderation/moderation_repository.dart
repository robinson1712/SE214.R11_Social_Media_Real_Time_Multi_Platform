import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/moderation.dart';
import '../../core/models/page_response.dart';

Report _reportFromJson(dynamic json) => Report.fromJson(json as Map<String, dynamic>);

class ModerationRepository {
  final Dio _dio = DioClient.instance;

  Future<Report> createReport(CreateReportRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/moderation/reports',
        data: request.toJson(),
      );
      return ApiResponse.fromJson(response.data!, _reportFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Report>> myReports({int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/moderation/reports/mine',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _reportFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Report>> allReports({
    String? status,
    String? targetType,
    int page = 0,
    int size = 20,
  }) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/moderation/reports',
        queryParameters: {
          if (status != null) 'status': status,
          if (targetType != null) 'targetType': targetType,
          'page': page,
          'size': size,
        },
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _reportFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Report> resolve(String id, ResolveReportRequest request) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '/api/moderation/reports/$id/resolve',
        data: request.toJson(),
      );
      return ApiResponse.fromJson(response.data!, _reportFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}

final moderationRepositoryProvider = Provider((ref) => ModerationRepository());
