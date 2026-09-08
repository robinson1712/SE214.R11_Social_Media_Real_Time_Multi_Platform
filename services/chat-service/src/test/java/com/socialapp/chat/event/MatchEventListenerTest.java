package com.socialapp.chat.event;

import com.socialapp.chat.document.Conversation;
import com.socialapp.chat.document.ConversationType;
import com.socialapp.chat.repository.ConversationRepository;
import com.socialapp.chat.repository.MessageRepository;
import com.socialapp.chat.service.ChatService;
import com.socialapp.common.event.MatchEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MatchEventListener, invoking the @KafkaListener-annotated
 * onMatch method directly (no real Kafka broker). Wires a real ChatService
 * against mocked repositories so the "only if one doesn't already exist"
 * dedup rule is exercised end-to-end, not just delegation.
 */
@ExtendWith(MockitoExtension.class)
class MatchEventListenerTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;

    private MatchEventListener matchEventListener;

    @BeforeEach
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ChatService chatService = new ChatService(conversationRepository, messageRepository, redisTemplate);
        matchEventListener = new MatchEventListener(chatService);
    }

    @Test
    void onMatch_noExistingPrivateConversation_createsOneBetweenBothUsers() {
        when(conversationRepository.findPrivateConversation("alice", "bob")).thenReturn(Optional.empty());
        MatchEvent event = new MatchEvent("match-1", "alice", "bob", Instant.now());

        matchEventListener.onMatch(event);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(ConversationType.PRIVATE);
        assertThat(captor.getValue().getParticipantIds()).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void onMatch_privateConversationAlreadyExists_doesNotCreateDuplicate() {
        Conversation existing = Conversation.builder()
                .id("conv-1")
                .type(ConversationType.PRIVATE)
                .participantIds(List.of("alice", "bob"))
                .createdAt(Instant.now())
                .build();
        when(conversationRepository.findPrivateConversation("alice", "bob")).thenReturn(Optional.of(existing));
        MatchEvent event = new MatchEvent("match-1", "alice", "bob", Instant.now());

        matchEventListener.onMatch(event);

        verify(conversationRepository, never()).save(any());
    }
}
