package com.socialapp.notification.config;

import com.socialapp.notification.security.UserHandshakeHandler;
import com.socialapp.notification.security.UserHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final UserHandshakeInterceptor userHandshakeInterceptor;
    private final UserHandshakeHandler userHandshakeHandler;

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
                .setAllowedOriginPatterns("*")
                .addInterceptors(userHandshakeInterceptor)
                .setHandshakeHandler(userHandshakeHandler)
                .withSockJS();
    }
}
