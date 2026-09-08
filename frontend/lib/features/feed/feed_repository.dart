import 'package:dio/dio.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/post.dart';

class FeedRepository {
  final Dio _dio = DioClient.instance;

  Future<List<Post>> myFeed({int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/feed/me',
        queryParameters: {'page': page, 'size': size},
      );
      final list = ApiResponse.fromJson(
        response.data!,
        (json) => (json as List<dynamic>)
            .map((e) => Post.fromJson(e as Map<String, dynamic>))
            .toList(),
      ).data;
      return list ?? const [];
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  /// Best-effort — a failed "seen" ping shouldn't disrupt the feed itself,
  /// so callers fire this off without awaiting/handling errors.
  Future<void> markSeen(List<String> postIds) async {
    if (postIds.isEmpty) return;
    try {
      await _dio.post<void>('/api/feed/seen', data: {'postIds': postIds});
    } on DioException {
      // Ignored deliberately — see doc comment above.
    }
  }
}
