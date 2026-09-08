import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/dio_client.dart';
import '../../core/models/api_response.dart';
import '../../core/models/chat.dart';
import '../../core/models/page_response.dart';

Conversation _conversationFromJson(dynamic json) =>
    Conversation.fromJson(json as Map<String, dynamic>);

ChatMessage _messageFromJson(dynamic json) =>
    ChatMessage.fromJson(json as Map<String, dynamic>);

class ChatRepository {
  final Dio _dio = DioClient.instance;

  Future<Conversation> createConversation(CreateConversationRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/chat/conversations',
        data: request.toJson(),
      );
      return ApiResponse.fromJson(response.data!, _conversationFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<List<Conversation>> conversations() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/api/chat/conversations');
      final list = ApiResponse.fromJson(
        response.data!,
        (json) => (json as List<dynamic>)
            .map((e) => Conversation.fromJson(e as Map<String, dynamic>))
            .toList(),
      ).data;
      return list ?? const [];
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<PageResponse<ChatMessage>> messages(String conversationId, {int page = 0, int size = 20}) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/chat/conversations/$conversationId/messages',
        queryParameters: {'page': page, 'size': size},
      );
      return ApiResponse.fromJson(
        response.data!,
        (json) => PageResponse.fromJson(json as Map<String, dynamic>, _messageFromJson),
      ).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<void> markRead(String conversationId) async {
    try {
      await _dio.post<void>('/api/chat/conversations/$conversationId/read');
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<ChatMessage> deleteMessage(String id) async {
    try {
      final response = await _dio.delete<Map<String, dynamic>>('/api/chat/messages/$id');
      return ApiResponse.fromJson(response.data!, _messageFromJson).data!;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<bool> presence(String userId) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/api/chat/presence/$userId');
      final data = response.data!['data'] as Map<String, dynamic>?;
      return data?['online'] as bool? ?? false;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  Future<Map<String, bool>> presenceBatch(List<String> userIds) async {
    if (userIds.isEmpty) return const {};
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/chat/presence/batch',
        data: {'userIds': userIds},
      );
      final data = response.data!['data'] as Map<String, dynamic>?;
      return (data ?? const {}).map((k, v) => MapEntry(k, v as bool));
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}

final chatRepositoryProvider = Provider((ref) => ChatRepository());
