package com.socialapp.notification.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Reads the X-User-Id header off the WebSocket upgrade HTTP request (forwarded
 * by the API Gateway after JWT validation) and stashes it in the handshake
 * attributes so the {@link UserHandshakeHandler} can turn it into a Principal.
 */
@Component
public class UserHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String userId = request.getHeaders().getFirst(USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            return false;
        }
        attributes.put("userId", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
