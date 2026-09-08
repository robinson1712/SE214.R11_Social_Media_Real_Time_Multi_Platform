import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/dating.dart';
import '../../core/models/page_response.dart';

DatingProfile _profileFromJson(dynamic json) =>
    DatingProfile.fromJson(json as Map<String, dynamic>);

CandidateResponse _candidateFromJson(dynamic json) =>
    CandidateResponse.fromJson(json as Map<String, dynamic>);

Match _matchFromJson(dynamic json) => Match.fromJson(json as Map<String, dynamic>);

class DatingRepository {
  final Dio _dio = DioClient.instance;

  Future<DatingProfile> upsertProfile(UpsertProfileRequest request) async {
    try {
      final response =
          await _dio.put<Map<String, dynamic>>('/api/dating/profile', data: request.toJson());
      return ApiResponse.fromJson(response.data!, _profileFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<DatingProfile?> myProfile() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/api/dating/profile/me');
      return ApiResponse.fromJson(response.data!, _profileFromJson).data;
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) return null;
      throw toApiException(e);
    }
  }

  Future<PageResponse<CandidateResponse>> candidates({int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/dating/candidates',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _candidateFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<SwipeResponse> swipe(SwipeRequest request) async {
    try {
      final response =
          await _dio.post<Map<String, dynamic>>('/api/dating/swipe', data: request.toJson());
      return ApiResponse.fromJson(
        response.data!,
        (json) => SwipeResponse.fromJson(json as Map<String, dynamic>),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<Match>> matches({int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/dating/matches',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _matchFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> unmatch(String matchId) async {
    try {
      await _dio.delete<void>('/api/dating/matches/$matchId');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}

final datingRepositoryProvider = Provider((ref) => DatingRepository());
