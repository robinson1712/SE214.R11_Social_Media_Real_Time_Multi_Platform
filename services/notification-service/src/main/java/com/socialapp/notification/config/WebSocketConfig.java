package com.socialapp.notification.config;

import com.socialapp.notification.security.UserHandshakeHandler;
import com.socialapp.notification.security.UserHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final UserHandshakeInterceptor userHandshakeInterceptor;
    private final UserHandshakeHandler userHandshakeHandler;
    private final com.socialapp.notification.security.NotificationStompInboundGuard inboundGuard;

    @Value("${app.security.allowed-origins:${CORS_ALLOWED_ORIGINS:http://localhost:3001}}")
    private String allowedOrigins;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(inboundGuard);
        registration.taskExecutor().corePoolSize(1).maxPoolSize(4).queueCapacity(100);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(16_384);
        registration.setSendBufferSizeLimit(128 * 1024);
        registration.setSendTimeLimit(15_000);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Distinct path from chat-service's own "/ws" — api-gateway can only route a given
        // path prefix to one downstream service, so the two STOMP endpoints can't share a name.
        registry.addEndpoint("/ws-notifications")
                .setAllowedOrigins(originPatterns())
                .addInterceptors(userHandshakeInterceptor)
                .setHandshakeHandler(userHandshakeHandler)
                .withSockJS();
    }

    private String[] originPatterns() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (origins.isEmpty() || origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("Set explicit app.security.allowed-origins for WebSocket");
        }
        return origins.toArray(String[]::new);
    }
}
