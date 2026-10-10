package com.socialapp.gateway.config;

import com.socialapp.common.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Configuration
public class GatewayConfig {

    /**
     * JwtTokenProvider is a plain POJO (jjwt only, no servlet dependency), so it is
     * safe to instantiate directly on the reactive (WebFlux) stack used by the gateway
     * without pulling in common-lib's servlet-based beans.
     */
    @Bean
    public JwtTokenProvider jwtTokenProvider(
            @Value("${jwt.secret:change-this-social-app-jwt-secret-key-must-be-at-least-256-bits}") String secret,
            @Value("${jwt.access-expiration-ms:900000}") long accessExp,
            @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExp) {
        return new JwtTokenProvider(secret, accessExp, refreshExp);
    }

    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${app.cors.allowed-origins:http://localhost:3001}") String configuredOrigins) {
        List<String> allowedOrigins = Arrays.stream(configuredOrigins.split(",", -1))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (allowedOrigins.isEmpty() || allowedOrigins.contains("*")) {
            throw new IllegalStateException("Configure explicit app.cors.allowed-origins; wildcard CORS is not allowed");
        }

        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(allowedOrigins);
        corsConfig.setMaxAge(3600L);
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        corsConfig.setExposedHeaders(List.of("Retry-After"));
        corsConfig.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        source.registerCorsConfiguration("/**", corsConfig);
        return new CorsWebFilter(source);
    }

    /**
     * Resolves the Redis rate-limiter bucket key. JwtAuthenticationFilter runs
     * before route filters (order -100) and, for an authenticated call, has
     * already stamped X-User-Id onto the request — so an authenticated user gets
     * one bucket per account regardless of which client/IP they call from.
     * Public routes (/api/auth/**) never get that header, so those fall back to
     * the caller's IP — the only identity available before login, and exactly
     * what needs throttling to slow down brute-force/credential-stuffing and
     * registration spam.
     */
    @Bean
    public KeyResolver userOrIpKeyResolver(
            @Value("${app.security.trusted-proxies:127.0.0.1,::1}") String trustedProxies) {
        TrustedProxyClientIpResolver clientIpResolver = new TrustedProxyClientIpResolver(trustedProxies);
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            return Mono.just(clientIpResolver.resolve(exchange.getRequest()));
        };
    }
}
