/// Request/response shapes for auth-service, matching
/// RegisterRequest/LoginRequest/AuthResponse/AccessTokenResponse/AccountResponse exactly.
library;

import 'enums.dart';

class RegisterRequest {
  final String email;
  final String password;
  final String fullName;
  final String? phone;
  final Gender? gender;
  final DateTime? dob;

  const RegisterRequest({
    required this.email,
    required this.password,
    required this.fullName,
    this.phone,
    this.gender,
    this.dob,
  });

  Map<String, dynamic> toJson() => {
        'email': email,
        'password': password,
        'fullName': fullName,
        if (phone != null) 'phone': phone,
        if (gender != null) 'gender': gender!.toJson(),
        if (dob != null)
          'dob':
              '${dob!.year.toString().padLeft(4, '0')}-${dob!.month.toString().padLeft(2, '0')}-${dob!.day.toString().padLeft(2, '0')}',
      };
}

class LoginRequest {
  final String email;
  final String password;

  const LoginRequest({required this.email, required this.password});

  Map<String, dynamic> toJson() => {'email': email, 'password': password};
}

class AuthResponse {
  final String accountId;
  final String email;
  final String accessToken;
  final String refreshToken;

  const AuthResponse({
    required this.accountId,
    required this.email,
    required this.accessToken,
    required this.refreshToken,
  });

  factory AuthResponse.fromJson(Map<String, dynamic> json) => AuthResponse(
        accountId: json['accountId'] as String,
        email: json['email'] as String,
        accessToken: json['accessToken'] as String,
        refreshToken: json['refreshToken'] as String,
      );
}

class AccessTokenResponse {
  final String accessToken;

  const AccessTokenResponse({required this.accessToken});

  factory AccessTokenResponse.fromJson(Map<String, dynamic> json) =>
      AccessTokenResponse(accessToken: json['accessToken'] as String);
}

class AccountResponse {
  final String id;
  final String email;
  final List<String> roles;
  final String status;

  const AccountResponse({
    required this.id,
    required this.email,
    required this.roles,
    required this.status,
  });

  bool get isAdmin => roles.contains('ADMIN');

  factory AccountResponse.fromJson(Map<String, dynamic> json) =>
      AccountResponse(
        id: json['id'] as String,
        email: json['email'] as String,
        roles: (json['roles'] as List<dynamic>? ?? const [])
            .map((e) => e as String)
            .toList(),
        status: json['status'] as String? ?? 'ACTIVE',
      );
}
