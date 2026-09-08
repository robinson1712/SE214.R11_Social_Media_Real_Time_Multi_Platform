package com.socialapp.chat.service;

import com.socialapp.chat.document.Conversation;
import com.socialapp.chat.document.ConversationType;
import com.socialapp.chat.document.Message;
import com.socialapp.chat.repository.ConversationRepository;
import com.socialapp.chat.repository.MessageRepository;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ChatService — conversation creation/dedup, membership-gated
 * message history and read receipts, and the private-conversation-on-match
 * helper used by MatchEventListener. Repositories and the Redis presence
 * template are mocked, so these exercise only ChatService's own decisions.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;

    private StringRedisTemplate redisTemplate;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        chatService = new ChatService(conversationRepository, messageRepository, redisTemplate);
    }

    @AfterEach
    void clearUser() {
        CurrentUserContext.clearForTests();
    }

    private Conversation conversation(String id, ConversationType type, List<String> participantIds) {
        return Conversation.builder()
                .id(id)
                .type(type)
                .participantIds(new ArrayList<>(participantIds))
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void createConversation_existingPrivateConversation_returnsExistingWithoutSavingAgain() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        Conversation existing = conversation("conv-1", ConversationType.PRIVATE, List.of("alice", "bob"));
        when(conversationRepository.findPrivateConversation(anyString(), anyString())).thenReturn(Optional.of(existing));

        Conversation result = chatService.createConversation(List.of("bob"));

        assertThat(result).isSameAs(existing);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void createConversation_noExistingPrivateConversation_createsNewOne() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        when(conversationRepository.findPrivateConversation(anyString(), anyString())).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation result = chatService.createConversation(List.of("bob"));

        assertThat(result.getType()).isEqualTo(ConversationType.PRIVATE);
        assertThat(result.getParticipantIds()).containsExactlyInAnyOrder("alice", "bob");
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void createConversation_threeOrMoreDistinctParticipants_createsGroupConversation() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation result = chatService.createConversation(List.of("bob", "carol"));

        assertThat(result.getType()).isEqualTo(ConversationType.GROUP);
        assertThat(result.getParticipantIds()).containsExactlyInAnyOrder("alice", "bob", "carol");
        verify(conversationRepository, never()).findPrivateConversation(anyString(), anyString());
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void createConversation_callerOmittedFromRequest_isStillIncludedAsParticipant() {
        CurrentUserContext.setForTests("alice", List.of("USER"));
        when(conversationRepository.findPrivateConversation(anyString(), anyString())).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Request lists only "bob" — caller "alice" must still end up a participant.
        Conversation result = chatService.createConversation(List.of("bob"));

        assertThat(result.getParticipantIds()).contains("alice");
    }

    @Test
    void getConversations_sortsByLastMessageAtOrCreatedAtDescending() {
        Instant now = Instant.now();
        Conversation older = conversation("conv-old", ConversationType.PRIVATE, List.of("alice", "bob"));
        older.setCreatedAt(now.minusSeconds(100));
        older.setLastMessageAt(now.minusSeconds(50));

        Conversation newer = conversation("conv-new", ConversationType.PRIVATE, List.of("alice", "carol"));
        newer.setCreatedAt(now.minusSeconds(200));
        newer.setLastMessageAt(now.minusSeconds(10));

        Conversation noMessagesYet = conversation("conv-nomsg", ConversationType.PRIVATE, List.of("alice", "dave"));
        noMessagesYet.setCreatedAt(now);
        noMessagesYet.setLastMessageAt(null);

        when(conversationRepository.findByParticipantIdsContaining("alice"))
                .thenReturn(new ArrayList<>(List.of(older, newer, noMessagesYet)));

        List<Conversation> result = chatService.getConversations("alice");

        assertThat(result).extracting(Conversation::getId)
                .containsExactly("conv-nomsg", "conv-new", "conv-old");
    }

    @Test
    void getMessages_participant_returnsPage() {
        Conversation conv = conversation("conv-1", ConversationType.PRIVATE, List.of("alice", "bob"));
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));
        Page<Message> page = new PageImpl<>(List.of(mock(Message.class)));
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(messageRepository.findByConversationIdOrderBySentAtDesc("conv-1", pageable)).thenReturn(page);

        Page<Message> result = chatService.getMessages("conv-1", "alice", pageable);

        assertThat(result).isSameAs(page);
    }

    @Test
    void getMessages_notParticipant_throwsForbidden() {
        Conversation conv = conversation("conv-1", ConversationType.PRIVATE, List.of("alice", "bob"));
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);

        assertThatThrownBy(() -> chatService.getMessages("conv-1", "eve", pageable))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).findByConversationIdOrderBySentAtDesc(anyString(), any());
    }

    @Test
    void getMessages_conversationNotFound_throwsResourceNotFound() {
        when(conversationRepository.findById("missing")).thenReturn(Optional.empty());
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);

        assertThatThrownBy(() -> chatService.getMessages("missing", "alice", pageable))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markRead_participant_addsUserToReadByAndSavesOnlyChangedMessages() {
        Conversation conv = conversation("conv-1", ConversationType.PRIVATE, List.of("alice", "bob"));
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));

        Message unread = Message.builder().id("m1").conversationId("conv-1").readBy(new HashSet<>()).build();
        Message alreadyRead = Message.builder().id("m2").conversationId("conv-1").readBy(new HashSet<>(List.of("alice"))).build();
        when(messageRepository.findByConversationId("conv-1")).thenReturn(List.of(unread, alreadyRead));

        chatService.markRead("conv-1", "alice");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, org.mockito.Mockito.times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("m1");
        assertThat(unread.getReadBy()).contains("alice");
    }

    @Test
    void markRead_notParticipant_throwsForbidden() {
        Conversation conv = conversation("conv-1", ConversationType.PRIVATE, List.of("alice", "bob"));
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> chatService.markRead("conv-1", "eve"))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).findByConversationId(anyString());
    }

    @Test
    void isOnline_presenceKeyExists_returnsTrue() {
        when(redisTemplate.hasKey("presence:alice")).thenReturn(true);

        assertThat(chatService.isOnline("alice")).isTrue();
    }

    @Test
    void isOnline_presenceKeyMissing_returnsFalse() {
        when(redisTemplate.hasKey("presence:alice")).thenReturn(false);

        assertThat(chatService.isOnline("alice")).isFalse();
    }

    @Test
    void onlineStatuses_mixedPresence_returnsPerUserMap() {
        when(redisTemplate.hasKey("presence:alice")).thenReturn(true);
        when(redisTemplate.hasKey("presence:bob")).thenReturn(false);

        var result = chatService.onlineStatuses(List.of("alice", "bob"));

        assertThat(result).containsEntry("alice", true).containsEntry("bob", false);
    }

    @Test
    void ensurePrivateConversation_noExistingConversation_createsOne() {
        when(conversationRepository.findPrivateConversation("alice", "bob")).thenReturn(Optional.empty());

        chatService.ensurePrivateConversation("alice", "bob");

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(ConversationType.PRIVATE);
        assertThat(captor.getValue().getParticipantIds()).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void ensurePrivateConversation_existingConversation_doesNotCreateDuplicate() {
        when(conversationRepository.findPrivateConversation("alice", "bob"))
                .thenReturn(Optional.of(conversation("conv-1", ConversationType.PRIVATE, List.of("alice", "bob"))));

        chatService.ensurePrivateConversation("alice", "bob");

        verify(conversationRepository, never()).save(any());
    }

    @Test
    void getConversationOrThrow_missing_throwsResourceNotFound() {
        when(conversationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getConversationOrThrow("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- deleteMessage ----------

    @Test
    void deleteMessage_bySender_scrubsContentAndMarksDeleted() {
        Message message = Message.builder().id("m1").conversationId("conv-1").senderId("alice")
                .content("secret").mediaUrl("http://media/1.png").sentAt(Instant.now()).build();
        when(messageRepository.findById("m1")).thenReturn(Optional.of(message));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        Message result = chatService.deleteMessage("m1", "alice");

        assertThat(result.isDeleted()).isTrue();
        assertThat(result.getContent()).isNull();
        assertThat(result.getMediaUrl()).isNull();
    }

    @Test
    void deleteMessage_byNonSender_throwsForbiddenAndNeverSaves() {
        Message message = Message.builder().id("m1").conversationId("conv-1").senderId("alice")
                .content("secret").sentAt(Instant.now()).build();
        when(messageRepository.findById("m1")).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatService.deleteMessage("m1", "bob"))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).save(any());
    }

    @Test
    void deleteMessage_missing_throwsResourceNotFound() {
        when(messageRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.deleteMessage("missing", "alice"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
