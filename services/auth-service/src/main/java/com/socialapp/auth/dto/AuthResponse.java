package com.socialapp.auth.dto;

public record AuthResponse(
        String accountId,
        String email,
        String accessToken,
        String refreshToken
) {
}
