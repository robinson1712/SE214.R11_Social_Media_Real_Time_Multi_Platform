package com.socialapp.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Reads the X-User-Id / X-User-Roles headers set by api-gateway (after JWT
 * validation) and exposes them via {@link CurrentUserContext} for the
 * duration of the request. Services trust these headers because they only
 * accept traffic from the gateway on the internal Docker network.
 */
@Component
@Order(1)
public class HeaderAuthFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String userId = request.getHeader(USER_ID_HEADER);
            String rolesHeader = request.getHeader(USER_ROLES_HEADER);
            List<String> roles = (rolesHeader == null || rolesHeader.isBlank())
                    ? Collections.emptyList()
                    : Arrays.asList(rolesHeader.split(","));
            if (userId != null) {
                CurrentUserContext.set(userId, roles);
            }
            filterChain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }
}
