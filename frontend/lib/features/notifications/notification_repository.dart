import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/app_notification.dart';
import '../../core/models/page_response.dart';

AppNotification _notificationFromJson(dynamic json) =>
    AppNotification.fromJson(json as Map<String, dynamic>);

class NotificationRepository {
  final Dio _dio = DioClient.instance;

  Future<PageResponse<AppNotification>> list({int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/notifications/me',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _notificationFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<AppNotification> markRead(String id) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>('/api/notifications/$id/read');
      return ApiResponse.fromJson(response.data!, _notificationFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> markAllRead() async {
    try {
      await _dio.put<void>('/api/notifications/read-all');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<int> unreadCount() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/api/notifications/unread-count');
      final data = response.data!['data'] as Map<String, dynamic>?;
      return (data?['count'] as num?)?.toInt() ?? 0;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> delete(String id) async {
    try {
      await _dio.delete<void>('/api/notifications/$id');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}

final notificationRepositoryProvider = Provider((ref) => NotificationRepository());
