import 'package:dio/dio.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/auth_models.dart';

class AuthRepository {
  final Dio _dio = DioClient.instance;

  Future<AuthResponse> register(RegisterRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/auth/register',
        data: request.toJson(),
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => AuthResponse.fromJson(json as Map<String, dynamic>),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<AuthResponse> login(LoginRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/auth/login',
        data: request.toJson(),
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => AuthResponse.fromJson(json as Map<String, dynamic>),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<AccountResponse> me() async {
    try {
      final response =
          await _dio.get<Map<String, dynamic>>('/api/auth/me');
      return ApiResponse.fromJson(
        response.data!,
        (json) => AccountResponse.fromJson(json as Map<String, dynamic>),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> logout(String refreshToken) async {
    try {
      await _dio.post<void>(
        '/api/auth/logout',
        data: {'refreshToken': refreshToken},
      );
    } on DioException {
      // Best-effort: local session is cleared regardless by the caller.
    }
  }
}
