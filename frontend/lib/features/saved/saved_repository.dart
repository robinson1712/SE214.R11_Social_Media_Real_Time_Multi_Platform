import 'package:dio/dio.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/enums.dart';
import '../../core/models/page_response.dart';
import '../../core/models/saved_item.dart';

SavedItem _savedItemFromJson(dynamic json) => SavedItem.fromJson(json as Map<String, dynamic>);

class SavedRepository {
  final Dio _dio = DioClient.instance;

  Future<SavedItem> save(SaveItemRequest request) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>('/api/saved', data: request.toJson());
      return ApiResponse.fromJson(response.data!, _savedItemFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> unsave(TargetType targetType, String targetId) async {
    try {
      await _dio.delete<void>('/api/saved', queryParameters: {
        'targetType': targetType.toJson(),
        'targetId': targetId,
      });
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<SavedItem?> mine(TargetType targetType, String targetId) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/api/saved/me', queryParameters: {
        'targetType': targetType.toJson(),
        'targetId': targetId,
      });
      return ApiResponse.fromJson(response.data!, _savedItemFromJson).data;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<SavedItem>> listMine({int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/saved/mine',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _savedItemFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}
