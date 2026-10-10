package com.socialapp.chat.security;

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

class ChatStompInboundGuardTest {

    private ChatStompInboundGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ChatStompInboundGuard();
    }

    @Test
    void connectRequiresAnAuthenticatedPrincipal() {
        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.CONNECT, "s1", null, null, null, new byte[0]), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void onlyTheUserMessageQueueCanBeSubscribedTo() {
        connect("s1", "alice");

        assertThat(guard.preSend(frame(StompCommand.SUBSCRIBE, "s1", "alice", "/user/queue/messages", "sub-1", new byte[0]), null))
                .isNotNull();
        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SUBSCRIBE, "s1", "alice", "/topic/messages", "sub-2", new byte[0]), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void perUserConnectionLimitIsEnforcedAndDisconnectReleasesCapacity() {
        ReflectionTestUtils.setField(guard, "maxConnectionsPerUser", 1);
        connect("s1", "alice");

        assertThatThrownBy(() -> connect("s2", "alice"))
                .isInstanceOf(MessageDeliveryException.class);

        guard.preSend(frame(StompCommand.DISCONNECT, "s1", "alice", null, null, new byte[0]), null);
        assertThat(guard.preSend(frame(StompCommand.CONNECT, "s2", "alice", null, null, new byte[0]), null))
                .isNotNull();
    }

    @Test
    void oversizedFrameIsRejected() {
        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.CONNECT, "s1", "alice", null, null, new byte[65_537]), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void sendDestinationAndPerUserRateLimitAreEnforced() {
        ReflectionTestUtils.setField(guard, "sendBurst", 2);
        ReflectionTestUtils.setField(guard, "sendPerSecond", 0.0);
        connect("s1", "alice");

        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SEND, "s1", "alice", "/topic/messages", null, new byte[0]), null))
                .isInstanceOf(MessageDeliveryException.class);
        guard.preSend(frame(StompCommand.SEND, "s1", "alice", "/app/chat.send", null, new byte[0]), null);
        guard.preSend(frame(StompCommand.SEND, "s1", "alice", "/app/chat.send", null, new byte[0]), null);
        assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SEND, "s1", "alice", "/app/chat.send", null, new byte[0]), null))
                .isInstanceOf(MessageDeliveryException.class);
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
