package com.socialapp.notification.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Limits notification WebSocket sessions to authenticated user-scoped reads. */
@Component
public class NotificationStompInboundGuard implements ChannelInterceptor {

    private static final String NOTIFICATION_DESTINATION = "/user/queue/notifications";

    @Value("${stomp.limits.max-frame-bytes:16384}")
    private int maxFrameBytes = 16_384;
    @Value("${stomp.limits.max-connections-per-user:5}")
    private int maxConnectionsPerUser = 5;

    private final Object connectionLock = new Object();
    private final Map<String, String> sessionOwners = new ConcurrentHashMap<>();
    private final Map<String, Integer> connectionsByUser = new ConcurrentHashMap<>();
    private final Map<String, String> subscriptionsBySession = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        rejectOversizedFrame(message.getPayload());

        String sessionId = accessor.getSessionId();
        String actorId = principalId(accessor.getUser());
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            registerConnection(sessionId, actorId);
        } else if (command == StompCommand.SEND) {
            throw rejected("Notification socket does not accept client SEND frames");
        } else if (command == StompCommand.SUBSCRIBE) {
            requireConnected(sessionId, actorId);
            if (!NOTIFICATION_DESTINATION.equals(accessor.getDestination())) {
                throw rejected("Unsupported notification subscription destination");
            }
            String subscriptionId = accessor.getSubscriptionId();
            if (subscriptionId == null || subscriptionId.isBlank()
                    || subscriptionsBySession.putIfAbsent(sessionId, subscriptionId) != null) {
                throw rejected("Notification subscription limit reached");
            }
        } else if (command == StompCommand.UNSUBSCRIBE) {
            requireConnected(sessionId, actorId);
            subscriptionsBySession.remove(sessionId, accessor.getSubscriptionId());
        } else if (command == StompCommand.DISCONNECT) {
            releaseConnection(sessionId);
        } else {
            throw rejected("Unsupported client STOMP command");
        }
        return message;
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        releaseConnection(event.getSessionId());
    }

    private void rejectOversizedFrame(Object payload) {
        int byteCount = payload instanceof byte[] bytes ? bytes.length
                : payload instanceof String text ? text.getBytes(StandardCharsets.UTF_8).length : 0;
        if (byteCount > maxFrameBytes) {
            throw rejected("STOMP frame exceeds configured size limit");
        }
    }

    private void registerConnection(String sessionId, String actorId) {
        if (sessionId == null || actorId == null) {
            throw rejected("Authenticated STOMP session required");
        }
        synchronized (connectionLock) {
            if (sessionOwners.containsKey(sessionId)) {
                return;
            }
            int current = connectionsByUser.getOrDefault(actorId, 0);
            if (current >= maxConnectionsPerUser) {
                throw rejected("Concurrent STOMP connection limit reached");
            }
            sessionOwners.put(sessionId, actorId);
            connectionsByUser.put(actorId, current + 1);
        }
    }

    private void requireConnected(String sessionId, String actorId) {
        if (sessionId == null || actorId == null || !actorId.equals(sessionOwners.get(sessionId))) {
            throw rejected("Authenticated STOMP session required");
        }
    }

    private void releaseConnection(String sessionId) {
        if (sessionId == null) return;
        synchronized (connectionLock) {
            String actorId = sessionOwners.remove(sessionId);
            subscriptionsBySession.remove(sessionId);
            if (actorId == null) return;
            int remaining = connectionsByUser.getOrDefault(actorId, 0) - 1;
            if (remaining <= 0) connectionsByUser.remove(actorId);
            else connectionsByUser.put(actorId, remaining);
        }
    }

    private String principalId(Principal principal) {
        return principal == null || principal.getName() == null || principal.getName().isBlank()
                ? null : principal.getName();
    }

    private MessageDeliveryException rejected(String message) {
        return new MessageDeliveryException(message);
    }
}
