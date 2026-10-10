package com.socialapp.common.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(
            "test-secret-that-is-at-least-256-bits-long-for-hmac-sha-256", 60_000, 60_000);

    @Test
    void validatesOnlyAccessTokensForApiAuthorization() {
        String access = provider.generateAccessToken("user-1", List.of("USER"));
        String refresh = provider.generateRefreshToken("user-1");

        assertThat(provider.validateAccessToken(access)).isTrue();
        assertThat(provider.validateAccessToken(refresh)).isFalse();
        assertThat(provider.validateToken(refresh)).isTrue();
    }

    @Test
    void rejectsMalformedAndUnsignedTokens() {
        assertThat(provider.validateAccessToken("not-a-jwt")).isFalse();
    }
}
