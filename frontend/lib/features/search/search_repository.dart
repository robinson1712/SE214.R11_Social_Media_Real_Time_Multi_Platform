import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import 'search_models.dart';

class SearchRepository {
  final Dio _dio = DioClient.instance;

  Future<SearchResults> search(String q, {int limit = 5}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/search',
        queryParameters: {'q': q, 'limit': limit},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => SearchResults.fromJson(json as Map<String, dynamic>),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}

final searchRepositoryProvider = Provider((ref) => SearchRepository());
