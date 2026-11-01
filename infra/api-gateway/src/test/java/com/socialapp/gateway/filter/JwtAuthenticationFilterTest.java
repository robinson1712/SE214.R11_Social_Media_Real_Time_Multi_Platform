package com.socialapp.gateway.filter;

import com.socialapp.common.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-that-is-at-least-256-bits-long-for-hmac-sha-256";

    private JwtTokenProvider tokenProvider;
    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, 60_000, 60_000);
        filter = new JwtAuthenticationFilter(tokenProvider);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void stripsClientIdentityHeadersBeforePublicRouteBypass() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login")
                        .header("X-User-Id", "admin")
                        .header("X-User-Roles", "ADMIN")
                        .build());

        filter.filter(exchange, chain).block();

        org.mockito.ArgumentCaptor<ServerWebExchange> forwarded = org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        assertThat(forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Roles")).isNull();
    }

    @Test
    void overwritesClientIdentityWithValidatedAccessTokenClaims() {
        String token = tokenProvider.generateAccessToken("alice", List.of("USER"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-User-Id", "admin")
                        .header("X-User-Roles", "ADMIN")
                        .build());

        filter.filter(exchange, chain).block();

        org.mockito.ArgumentCaptor<ServerWebExchange> forwarded = org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        assertThat(forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("alice");
        assertThat(forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Roles")).isEqualTo("USER");
    }

    @Test
    void rejectsRefreshTokensForProtectedRoutes() {
        String refresh = tokenProvider.generateRefreshToken("alice");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/me").header("Authorization", "Bearer " + refresh).build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }
}
