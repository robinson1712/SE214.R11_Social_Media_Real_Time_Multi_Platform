package com.socialapp.chat.presence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private static final String PRESENCE_PREFIX = "presence:";

    private final StringRedisTemplate redisTemplate;

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        Principal user = StompHeaderAccessor.wrap(event.getMessage()).getUser();
        if (user != null) {
            redisTemplate.opsForValue().set(PRESENCE_PREFIX + user.getName(), "online");
            log.debug("User {} connected, marked online", user.getName());
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        Principal user = StompHeaderAccessor.wrap(event.getMessage()).getUser();
        if (user != null) {
            redisTemplate.delete(PRESENCE_PREFIX + user.getName());
            log.debug("User {} disconnected, marked offline", user.getName());
        }
    }
}
