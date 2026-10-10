package com.socialapp.notification.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationStompInboundGuardTest {

    private NotificationStompInboundGuard guard;

    @BeforeEach
    void setUp() {
        guard = new NotificationStompInboundGuard();
    }

    @Test
    void onlyAuthenticatedUsersMayConnectAndSubscribeToTheirNotificationQueue() {
        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.CONNECT, "s0", null, null, null, new byte[0]), null))
                .isInstanceOf(MessageDeliveryException.class);

        connect("s1", "alice");
        assertThat(guard.preSend(frame(StompCommand.SUBSCRIBE, "s1", "alice", "/user/queue/notifications", "sub-1", new byte[0]), null))
                .isNotNull();
        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SUBSCRIBE, "s1", "alice", "/topic/notifications", "sub-2", new byte[0]), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void clientSendFramesAreRejected() {
        connect("s1", "alice");

        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SEND, "s1", "alice", "/app/notifications", null, new byte[0]), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connectionLimitAndFrameLimitAreEnforced() {
        ReflectionTestUtils.setField(guard, "maxConnectionsPerUser", 1);
        connect("s1", "alice");
        assertThatThrownBy(() -> connect("s2", "alice"))
                .isInstanceOf(MessageDeliveryException.class);

        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.CONNECT, "s3", "bob", null, null, new byte[16_385]), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void unsubscribeReleasesSubscriptionSlot() {
        connect("s1", "alice");
        guard.preSend(frame(StompCommand.SUBSCRIBE, "s1", "alice", "/user/queue/notifications", "sub-1", new byte[0]), null);
        guard.preSend(frame(StompCommand.UNSUBSCRIBE, "s1", "alice", null, "sub-1", new byte[0]), null);

        assertThat(guard.preSend(frame(StompCommand.SUBSCRIBE, "s1", "alice", "/user/queue/notifications", "sub-2", new byte[0]), null))
                .isNotNull();
    }

    private void connect(String sessionId, String userId) {
        guard.preSend(frame(StompCommand.CONNECT, sessionId, userId, null, null, new byte[0]), null);
    }

    private Message<byte[]> frame(StompCommand command, String sessionId, String userId,
                                  String destination, String subscriptionId, byte[] payload) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        if (userId != null) {
            accessor.setUser((Principal) () -> userId);
        }
        accessor.setDestination(destination);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }
}
