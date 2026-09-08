import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http_parser/http_parser.dart';

import '../models/enums.dart';
import 'dio_client.dart';

/// Shared by any feature that uploads a file to media-service
/// (post composer, profile avatar/cover, chat attachments).
class MediaRepository {
  final Dio _dio = DioClient.instance;

  Future<String> uploadBytes({
    required List<int> bytes,
    required String filename,
    required MediaPurpose purpose,
    // Dio infers content-type from `filename`'s extension when this is
    // omitted (via the `mime` package's lookup table), which is right for
    // picked files (their name already carries a real extension) but wrong
    // for the in-browser voice recorder: it hands back raw bytes with no
    // filename of its own, and the `mime` table maps ".webm" to
    // "video/webm" rather than "audio/webm" — media-service's whitelist
    // (see MediaService.AUDIO_CONTENT_TYPES) checks the exact MIME string,
    // so that mismatch would get every voice message rejected. Callers that
    // already know the real type (like the recorder) pass it explicitly.
    MediaType? contentType,
  }) async {
    try {
      final formData = FormData.fromMap({
        'file': MultipartFile.fromBytes(bytes, filename: filename, contentType: contentType),
        'purpose': purpose.toJson(),
      });
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/media/upload',
        data: formData,
      );
      final data = response.data!['data'] as Map<String, dynamic>;
      return data['url'] as String;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}

final mediaRepositoryProvider = Provider((ref) => MediaRepository());
