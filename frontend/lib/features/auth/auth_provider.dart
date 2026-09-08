import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/api_exception.dart';
import '../../core/api/dio_client.dart';
import '../../core/auth/token_storage.dart';
import '../../core/models/auth_models.dart';
import '../../core/models/enums.dart';
import '../chat/chat_provider.dart';
import '../notifications/notification_provider.dart';
import 'auth_repository.dart';

enum AuthStatus { unknown, authenticated, unauthenticated }

class AuthState {
  final AuthStatus status;
  final AccountResponse? account;
  final bool busy;
  final String? error;

  const AuthState({
    this.status = AuthStatus.unknown,
    this.account,
    this.busy = false,
    this.error,
  });

  AuthState copyWith({
    AuthStatus? status,
    AccountResponse? account,
    bool clearAccount = false,
    bool? busy,
    String? error,
    bool clearError = false,
  }) =>
      AuthState(
        status: status ?? this.status,
        account: clearAccount ? null : (account ?? this.account),
        busy: busy ?? this.busy,
        error: clearError ? null : (error ?? this.error),
      );
}

class AuthNotifier extends StateNotifier<AuthState> {
  final AuthRepository _repository;
  final Ref _ref;

  AuthNotifier(this._repository, this._ref) : super(const AuthState()) {
    DioClient.onSessionExpired = () {
      state = const AuthState(status: AuthStatus.unauthenticated);
      _resetSessionScopedProviders();
    };
    _bootstrap();
  }

  /// chatProvider/notificationProvider hold a live WebSocket connection plus
  /// per-user cached state (messages, unread counts) for the whole app
  /// lifetime — without this, switching accounts in the same browser tab
  /// (logout then log in as someone else) leaves the previous user's
  /// messages/connection alive, so "mine" vs "theirs" bubble alignment and
  /// unread badges end up computed against the wrong identity.
  void _resetSessionScopedProviders() {
    _ref.invalidate(chatProvider);
    _ref.invalidate(notificationProvider);
  }

  Future<void> _bootstrap() async {
    final hasSession = await TokenStorage.instance.hasSession();
    if (!hasSession) {
      state = const AuthState(status: AuthStatus.unauthenticated);
      return;
    }
    try {
      final account = await _repository.me();
      state = AuthState(status: AuthStatus.authenticated, account: account);
    } on ApiException {
      await TokenStorage.instance.clear();
      state = const AuthState(status: AuthStatus.unauthenticated);
    }
  }

  Future<bool> login(String email, String password) async {
    state = state.copyWith(busy: true, clearError: true);
    try {
      final auth = await _repository.login(
        LoginRequest(email: email, password: password),
      );
      await TokenStorage.instance.saveSession(
        accessToken: auth.accessToken,
        refreshToken: auth.refreshToken,
        accountId: auth.accountId,
      );
      final account = await _repository.me();
      state = AuthState(status: AuthStatus.authenticated, account: account);
      _resetSessionScopedProviders();
      return true;
    } on ApiException catch (e) {
      state = state.copyWith(busy: false, error: e.message);
      return false;
    }
  }

  Future<bool> register(
    String email,
    String password,
    String fullName, {
    String? phone,
    Gender? gender,
    DateTime? dob,
  }) async {
    state = state.copyWith(busy: true, clearError: true);
    try {
      final auth = await _repository.register(
        RegisterRequest(
          email: email,
          password: password,
          fullName: fullName,
          phone: phone,
          gender: gender,
          dob: dob,
        ),
      );
      await TokenStorage.instance.saveSession(
        accessToken: auth.accessToken,
        refreshToken: auth.refreshToken,
        accountId: auth.accountId,
      );
      final account = await _repository.me();
      state = AuthState(status: AuthStatus.authenticated, account: account);
      _resetSessionScopedProviders();
      return true;
    } on ApiException catch (e) {
      state = state.copyWith(busy: false, error: e.message);
      return false;
    }
  }

  Future<void> logout() async {
    final refreshToken = await TokenStorage.instance.readRefreshToken();
    if (refreshToken != null) {
      await _repository.logout(refreshToken);
    }
    await TokenStorage.instance.clear();
    state = const AuthState(status: AuthStatus.unauthenticated);
    _resetSessionScopedProviders();
  }
}

final authRepositoryProvider = Provider((ref) => AuthRepository());

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>(
  (ref) => AuthNotifier(ref.watch(authRepositoryProvider), ref),
);

/// The current user's account id, used throughout the app to tell
/// "mine" apart from other users' content (e.g. showing edit/delete
/// actions only on your own posts).
final currentAccountIdProvider = Provider<String?>(
  (ref) => ref.watch(authProvider).account?.id,
);
