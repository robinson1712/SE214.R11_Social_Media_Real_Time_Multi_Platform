import 'dart:async';

import 'package:dio/dio.dart';

import '../auth/token_storage.dart';
import 'api_exception.dart';

/// Base URL of api-gateway. CORS is already open there (`allowedOriginPatterns: *`),
/// so the Flutter web app calls it directly with no proxy.
const String apiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://localhost:8080',
);

/// Paths that must never get an `Authorization` header attached and must
/// never trigger a refresh-and-retry on 401 (they ARE the auth endpoints).
bool _isAuthEndpoint(String path) =>
    path.contains('/api/auth/register') ||
    path.contains('/api/auth/login') ||
    path.contains('/api/auth/refresh');

/// Builds the single shared Dio instance used by every repository.
///
/// Two responsibilities live here as interceptors:
/// 1. Attach `Authorization: Bearer <token>` to every request that isn't the
///    auth endpoints themselves.
/// 2. On a 401 response, attempt exactly one silent refresh via
///    `/api/auth/refresh`, then retry the original request once. If the
///    refresh itself fails, clear the stored session and notify
///    [onSessionExpired] so the router can redirect to `/login`.
class DioClient {
  DioClient._();

  static final Dio instance = _build();

  /// Set by the auth feature (e.g. the Riverpod auth notifier) so this layer
  /// can trigger a logout/redirect without depending on Riverpod or go_router.
  static void Function()? onSessionExpired;

  // Single-flight guard: concurrent 401s share one refresh call instead of
  // each firing their own request against auth-service.
  static Completer<String?>? _refreshCompleter;

  static Dio _build() {
    final dio = Dio(
      BaseOptions(
        baseUrl: apiBaseUrl,
        connectTimeout: const Duration(seconds: 15),
        receiveTimeout: const Duration(seconds: 15),
        contentType: 'application/json',
      ),
    );

    dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          if (!_isAuthEndpoint(options.path)) {
            final token = await TokenStorage.instance.readAccessToken();
            if (token != null) {
              options.headers['Authorization'] = 'Bearer $token';
            }
          }
          handler.next(options);
        },
        onError: (error, handler) async {
          final path = error.requestOptions.path;
          final status = error.response?.statusCode;

          // The client fires legitimate bursts of concurrent requests on a
          // single page load (per-post/comment author lookups, feed,
          // notifications, chat, presence/follow-status all at once), which
          // can occasionally outrun the gateway's rate limiter even after
          // raising it — retry transparently with backoff a couple of times
          // rather than surfacing a 429 as a hard failure to whatever
          // widget happened to fire the unlucky request.
          if (status == 429) {
            final attempt = (error.requestOptions.extra['retry429'] as int?) ?? 0;
            if (attempt < 3) {
              await Future.delayed(Duration(milliseconds: 300 * (attempt + 1)));
              try {
                final retryOptions = error.requestOptions;
                retryOptions.extra['retry429'] = attempt + 1;
                final response = await dio.fetch(retryOptions);
                handler.resolve(response);
              } on DioException catch (retryError) {
                handler.next(retryError);
              }
              return;
            }
          }

          if (status != 401 || _isAuthEndpoint(path)) {
            handler.next(error);
            return;
          }

          final newAccessToken = await _refreshAccessToken();
          if (newAccessToken == null) {
            await TokenStorage.instance.clear();
            onSessionExpired?.call();
            handler.next(error);
            return;
          }

          try {
            final retryOptions = error.requestOptions;
            retryOptions.headers['Authorization'] = 'Bearer $newAccessToken';
            final response = await dio.fetch(retryOptions);
            handler.resolve(response);
          } on DioException catch (retryError) {
            handler.next(retryError);
          }
        },
      ),
    );

    return dio;
  }

  static Future<String?> _refreshAccessToken() {
    if (_refreshCompleter != null) return _refreshCompleter!.future;

    final completer = Completer<String?>();
    _refreshCompleter = completer;

    () async {
      try {
        final refreshToken = await TokenStorage.instance.readRefreshToken();
        if (refreshToken == null) {
          completer.complete(null);
          return;
        }

        final refreshDio = Dio(BaseOptions(baseUrl: apiBaseUrl));
        final response = await refreshDio.post<Map<String, dynamic>>(
          '/api/auth/refresh',
          data: {'refreshToken': refreshToken},
        );

        final newAccessToken =
            (response.data?['data'] as Map<String, dynamic>?)?['accessToken']
                as String?;
        if (newAccessToken == null) {
          completer.complete(null);
          return;
        }

        await TokenStorage.instance.saveAccessToken(newAccessToken);
        completer.complete(newAccessToken);
      } catch (_) {
        completer.complete(null);
      } finally {
        _refreshCompleter = null;
      }
    }();

    return completer.future;
  }
}

/// Converts a [DioException] into an [ApiException] carrying the backend's
/// `ApiResponse.message` when present, so UI code can show it directly.
ApiException toApiException(DioException error) {
  final data = error.response?.data;
  String? message;
  if (data is Map<String, dynamic>) {
    message = data['message'] as String?;
  }
  return ApiException(
    message: message ?? error.message ?? 'Đã có lỗi xảy ra',
    statusCode: error.response?.statusCode,
  );
}
