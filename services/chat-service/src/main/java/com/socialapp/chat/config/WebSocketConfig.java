package com.socialapp.chat.config;

import com.socialapp.chat.security.UserHandshakeHandler;
import com.socialapp.chat.security.UserHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final UserHandshakeInterceptor userHandshakeInterceptor;
    private final UserHandshakeHandler userHandshakeHandler;
    private final com.socialapp.chat.security.ChatStompInboundGuard inboundGuard;

    @Value("${app.security.allowed-origins:${CORS_ALLOWED_ORIGINS:http://localhost:3001}}")
    private String allowedOrigins;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(inboundGuard);
        registration.taskExecutor().corePoolSize(2).maxPoolSize(8).queueCapacity(100);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(65_536);
        registration.setSendBufferSizeLimit(256 * 1024);
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
        registry.addEndpoint("/ws")
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
