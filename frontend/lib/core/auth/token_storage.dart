import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Thin wrapper around `flutter_secure_storage` for the two JWT tokens.
/// Kept as a single class (not a Riverpod provider) so the Dio interceptor,
/// which is constructed before any `ProviderContainer` exists, can use it
/// directly.
class TokenStorage {
  TokenStorage._();

  static final TokenStorage instance = TokenStorage._();

  final _storage = const FlutterSecureStorage();

  static const _accessTokenKey = 'access_token';
  static const _refreshTokenKey = 'refresh_token';
  static const _accountIdKey = 'account_id';

  Future<String?> readAccessToken() => _storage.read(key: _accessTokenKey);
  Future<String?> readRefreshToken() => _storage.read(key: _refreshTokenKey);
  Future<String?> readAccountId() => _storage.read(key: _accountIdKey);

  Future<void> saveSession({
    required String accessToken,
    required String refreshToken,
    required String accountId,
  }) async {
    await _storage.write(key: _accessTokenKey, value: accessToken);
    await _storage.write(key: _refreshTokenKey, value: refreshToken);
    await _storage.write(key: _accountIdKey, value: accountId);
  }

  Future<void> saveAccessToken(String accessToken) =>
      _storage.write(key: _accessTokenKey, value: accessToken);

  Future<void> clear() async {
    await _storage.delete(key: _accessTokenKey);
    await _storage.delete(key: _refreshTokenKey);
    await _storage.delete(key: _accountIdKey);
  }

  Future<bool> hasSession() async =>
      (await readAccessToken()) != null && (await readRefreshToken()) != null;
}
