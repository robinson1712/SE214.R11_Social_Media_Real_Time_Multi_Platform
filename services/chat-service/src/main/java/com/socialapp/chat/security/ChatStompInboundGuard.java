package com.socialapp.chat.security;

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
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Enforces limits on STOMP frames after the HTTP WebSocket upgrade. */
@Component
public class ChatStompInboundGuard implements ChannelInterceptor {

    private static final String MESSAGE_DESTINATION = "/user/queue/messages";
    private static final String SEND_DESTINATION = "/app/chat.send";

    @Value("${stomp.limits.max-frame-bytes:65536}")
    private int maxFrameBytes = 65_536;
    @Value("${stomp.limits.max-connections-per-user:5}")
    private int maxConnectionsPerUser = 5;
    @Value("${stomp.limits.max-subscriptions-per-connection:5}")
    private int maxSubscriptionsPerConnection = 5;
    @Value("${stomp.limits.send-burst:20}")
    private int sendBurst = 20;
    @Value("${stomp.limits.send-per-second:1}")
    private double sendPerSecond = 1.0;

    private final Object connectionLock = new Object();
    private final Map<String, String> sessionOwners = new ConcurrentHashMap<>();
    private final Map<String, Integer> connectionsByUser = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> subscriptionsBySession = new ConcurrentHashMap<>();
    private final Map<String, TokenBucket> sendBuckets = new ConcurrentHashMap<>();

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
            requireConnected(sessionId, actorId);
            if (!SEND_DESTINATION.equals(accessor.getDestination())) {
                throw rejected("Unsupported STOMP SEND destination");
            }
            if (!takeSendToken(actorId)) {
                throw rejected("STOMP send rate exceeded");
            }
        } else if (command == StompCommand.SUBSCRIBE) {
            requireConnected(sessionId, actorId);
            if (!MESSAGE_DESTINATION.equals(accessor.getDestination())) {
                throw rejected("Unsupported STOMP subscription destination");
            }
            registerSubscription(sessionId, accessor.getSubscriptionId(), accessor.getDestination());
        } else if (command == StompCommand.UNSUBSCRIBE) {
            requireConnected(sessionId, actorId);
            unregisterSubscription(sessionId, accessor.getSubscriptionId());
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
        int byteCount = 0;
        if (payload instanceof byte[] bytes) {
            byteCount = bytes.length;
        } else if (payload instanceof String text) {
            byteCount = text.getBytes(StandardCharsets.UTF_8).length;
        }
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
            subscriptionsBySession.put(sessionId, new ConcurrentHashMap<>());
        }
    }

    private void requireConnected(String sessionId, String actorId) {
        if (sessionId == null || actorId == null || !actorId.equals(sessionOwners.get(sessionId))) {
            throw rejected("Authenticated STOMP session required");
        }
    }

    private void registerSubscription(String sessionId, String subscriptionId, String destination) {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw rejected("STOMP subscription id is required");
        }
        Map<String, String> subscriptions = subscriptionsBySession.get(sessionId);
        if (subscriptions == null) {
            throw rejected("STOMP connection is not registered");
        }
        synchronized (subscriptions) {
            if (subscriptions.size() >= maxSubscriptionsPerConnection
                    || subscriptions.containsValue(destination)) {
                throw rejected("STOMP subscription limit reached");
            }
            subscriptions.put(subscriptionId, destination);
        }
    }

    private void unregisterSubscription(String sessionId, String subscriptionId) {
        Map<String, String> subscriptions = subscriptionsBySession.get(sessionId);
        if (subscriptions != null && subscriptionId != null) {
            subscriptions.remove(subscriptionId);
        }
    }

    private void releaseConnection(String sessionId) {
        if (sessionId == null) {
            return;
        }
        synchronized (connectionLock) {
            String actorId = sessionOwners.remove(sessionId);
            subscriptionsBySession.remove(sessionId);
            if (actorId == null) {
                return;
            }
            int remaining = connectionsByUser.getOrDefault(actorId, 0) - 1;
            if (remaining <= 0) {
                connectionsByUser.remove(actorId);
                sendBuckets.remove(actorId);
            } else {
                connectionsByUser.put(actorId, remaining);
            }
        }
    }

    private boolean takeSendToken(String actorId) {
        TokenBucket bucket = sendBuckets.computeIfAbsent(actorId, ignored -> new TokenBucket(sendBurst));
        return bucket.take(sendBurst, sendPerSecond, Instant.now().toEpochMilli());
    }

    private String principalId(Principal principal) {
        return principal == null || principal.getName() == null || principal.getName().isBlank()
                ? null : principal.getName();
    }

    private MessageDeliveryException rejected(String message) {
        return new MessageDeliveryException(message);
    }

    private static final class TokenBucket {
        private double tokens;
        private long updatedAtMillis;

        private TokenBucket(int capacity) {
            this.tokens = capacity;
            this.updatedAtMillis = Instant.now().toEpochMilli();
        }

        private synchronized boolean take(int capacity, double refillPerSecond, long nowMillis) {
            double elapsedSeconds = Math.max(0, nowMillis - updatedAtMillis) / 1000.0;
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            updatedAtMillis = nowMillis;
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }
    }
}
