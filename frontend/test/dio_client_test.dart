import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:social_media_frontend/core/api/dio_client.dart';
import 'package:social_media_frontend/core/auth/token_storage.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  HttpOverrides.global = null;
  late HttpServer server;
  late Future<void> Function(HttpRequest) respond;
  var expired = 0;

  Future<void> reply(HttpRequest request, int status, [Object? body]) async {
    await request.drain<void>();
    request.response.statusCode = status;
    request.response.headers.contentType = ContentType.json;
    request.response.write(jsonEncode(body ?? {'message': 'test response'}));
    await request.response.close();
  }

  setUp(() async {
    FlutterSecureStorage.setMockInitialValues({
      'access_token': 'old-access',
      'refresh_token': 'test-refresh',
      'account_id': 'test-account',
    });
    expired = 0;
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    server.listen((request) => respond(request));
    DioClient.instance.options.baseUrl = 'http://127.0.0.1:${server.port}';
    DioClient.onSessionExpired = () => expired++;
  });

  tearDown(() async {
    await server.close(force: true);
    DioClient.onSessionExpired = null;
  });

  test('a repeated 401 refreshes once, stops retrying and clears the session', () async {
    var protectedCalls = 0;
    var refreshCalls = 0;
    respond = (request) async {
      if (request.uri.path == '/api/auth/refresh') {
        refreshCalls++;
        await reply(request, 200, {'data': {'accessToken': 'new-access'}});
      } else {
        protectedCalls++;
        await reply(request, 401);
      }
    };
    await expectLater(DioClient.instance.get<dynamic>('/api/posts'), throwsA(isA<DioException>()));
    expect(protectedCalls, 2);
    expect(refreshCalls, 1);
    expect(expired, 1);
    expect(await TokenStorage.instance.hasSession(), isFalse);
  }, timeout: const Timeout(Duration(seconds: 5)));

  test('a successful refresh retries using the new access token', () async {
    var protectedCalls = 0;
    var refreshCalls = 0;
    respond = (request) async {
      if (request.uri.path == '/api/auth/refresh') {
        refreshCalls++;
        expect(request.headers.value(HttpHeaders.authorizationHeader), isNull);
        await reply(request, 200, {'data': {'accessToken': 'new-access'}});
      } else {
        protectedCalls++;
        await reply(request, request.headers.value(HttpHeaders.authorizationHeader) == 'Bearer new-access' ? 200 : 401);
      }
    };
    final response = await DioClient.instance.get<dynamic>('/api/posts');
    expect(response.statusCode, 200);
    expect(protectedCalls, 2);
    expect(refreshCalls, 1);
    expect(expired, 0);
    expect(await TokenStorage.instance.readAccessToken(), 'new-access');
  });

  test('concurrent 401 responses share one refresh request', () async {
    var refreshCalls = 0;
    respond = (request) async {
      if (request.uri.path == '/api/auth/refresh') {
        refreshCalls++;
        await Future<void>.delayed(const Duration(milliseconds: 100));
        await reply(request, 200, {'data': {'accessToken': 'new-access'}});
      } else {
        await reply(request, request.headers.value(HttpHeaders.authorizationHeader) == 'Bearer new-access' ? 200 : 401);
      }
    };
    final responses = await Future.wait(List.generate(3, (_) => DioClient.instance.get<dynamic>('/api/posts')));
    expect(responses.every((response) => response.statusCode == 200), isTrue);
    expect(refreshCalls, 1);
  });

  test('login 401 does not attach a token or trigger refresh', () async {
    var calls = 0;
    respond = (request) async {
      calls++;
      expect(request.uri.path, '/api/auth/login');
      expect(request.headers.value(HttpHeaders.authorizationHeader), isNull);
      await reply(request, 401);
    };
    await expectLater(DioClient.instance.post<dynamic>('/api/auth/login'), throwsA(isA<DioException>()));
    expect(calls, 1);
    expect(expired, 0);
  });

  test('429 retries stop after three retries', () async {
    var calls = 0;
    respond = (request) async {
      calls++;
      await reply(request, 429);
    };
    await expectLater(DioClient.instance.get<dynamic>('/api/posts'), throwsA(isA<DioException>()));
    expect(calls, 4);
  });
}
