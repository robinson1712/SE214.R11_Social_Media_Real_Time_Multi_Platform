package com.socialapp.chat.controller;

import com.socialapp.chat.document.Conversation;
import com.socialapp.chat.document.ConversationType;
import com.socialapp.chat.document.Message;
import com.socialapp.chat.dto.ChatSendRequest;
import com.socialapp.chat.repository.ConversationRepository;
import com.socialapp.chat.repository.MessageRepository;
import com.socialapp.chat.security.StompPrincipal;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.MessageEvent;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the STOMP message-send business logic. This lives directly
 * in ChatStompController rather than ChatService, so it's instantiated and
 * exercised the same way as a plain service — no Spring/WebSocket context,
 * SimpMessagingTemplate and KafkaTemplate are mocked. The @MessageMapping
 * handler itself (subscription wiring) is out of scope; only sendMessage's
 * own decisions are tested here.
 */
@ExtendWith(MockitoExtension.class)
class ChatStompControllerTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private ChatStompController controller;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        controller = new ChatStompController(conversationRepository, messageRepository, messagingTemplate, kafkaTemplate);
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
    void sendMessage_notAParticipant_throwsForbiddenAndNeverPersistsOrPublishes() {
        Conversation conv = conversation("conv-1", ConversationType.PRIVATE, List.of("alice", "bob"));
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));
        ChatSendRequest request = new ChatSendRequest("conv-1", "hi", null, null, null);

        assertThatThrownBy(() -> controller.sendMessage(request, new StompPrincipal("eve")))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void sendMessage_conversationNotFound_throwsResourceNotFound() {
        when(conversationRepository.findById("missing")).thenReturn(Optional.empty());
        ChatSendRequest request = new ChatSendRequest("missing", "hi", null, null, null);

        assertThatThrownBy(() -> controller.sendMessage(request, new StompPrincipal("alice")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sendMessage_privateConversation_savesUpdatesConversationPublishesToOtherParticipantAndBroadcastsToBoth() {
        Conversation conv = conversation("conv-1", ConversationType.PRIVATE, List.of("alice", "bob"));
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        ChatSendRequest request = new ChatSendRequest("conv-1", "hello bob", null, null, null);

        controller.sendMessage(request, new StompPrincipal("alice"));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSenderId()).isEqualTo("alice");
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("hello bob");
        assertThat(messageCaptor.getValue().getConversationId()).isEqualTo("conv-1");

        ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(convCaptor.capture());
        assertThat(convCaptor.getValue().getLastMessagePreview()).isEqualTo("hello bob");
        assertThat(convCaptor.getValue().getLastMessageAt()).isNotNull();

        ArgumentCaptor<MessageEvent> eventCaptor = ArgumentCaptor.forClass(MessageEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.MESSAGE), eq("conv-1"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().senderId()).isEqualTo("alice");
        assertThat(eventCaptor.getValue().recipientId()).isEqualTo("bob");
        assertThat(eventCaptor.getValue().preview()).isEqualTo("hello bob");

        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("alice"), eq("/queue/messages"), any(Message.class));
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("bob"), eq("/queue/messages"), any(Message.class));
    }

    @Test
    void sendMessage_groupConversation_recipientIdIsNullAndBroadcastsToAllParticipants() {
        Conversation conv = conversation("conv-2", ConversationType.GROUP, List.of("alice", "bob", "carol"));
        when(conversationRepository.findById("conv-2")).thenReturn(Optional.of(conv));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        ChatSendRequest request = new ChatSendRequest("conv-2", "hey everyone", null, null, null);

        controller.sendMessage(request, new StompPrincipal("alice"));

        ArgumentCaptor<MessageEvent> eventCaptor = ArgumentCaptor.forClass(MessageEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.MESSAGE), eq("conv-2"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().recipientId()).isNull();

        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("alice"), eq("/queue/messages"), any(Message.class));
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("bob"), eq("/queue/messages"), any(Message.class));
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("carol"), eq("/queue/messages"), any(Message.class));
    }

    @Test
    void sendMessage_storyReply_persistsStoryReplyFieldsOnMessage() {
        Conversation conv = conversation("conv-1", ConversationType.PRIVATE, List.of("alice", "bob"));
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        ChatSendRequest request = new ChatSendRequest(
                "conv-1", "nice story!", null, "story-1", "http://media/story-1.png");

        controller.sendMessage(request, new StompPrincipal("alice"));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getStoryReplyId()).isEqualTo("story-1");
        assertThat(messageCaptor.getValue().getStoryReplyPreviewUrl()).isEqualTo("http://media/story-1.png");
    }

    @Test
    void sendMessage_contentLongerThan100Chars_previewIsTruncated() {
        Conversation conv = conversation("conv-1", ConversationType.PRIVATE, List.of("alice", "bob"));
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(conv));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        String longContent = "x".repeat(150);
        ChatSendRequest request = new ChatSendRequest("conv-1", longContent, null, null, null);

        controller.sendMessage(request, new StompPrincipal("alice"));

        ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(convCaptor.capture());
        assertThat(convCaptor.getValue().getLastMessagePreview()).hasSize(100);

        ArgumentCaptor<MessageEvent> eventCaptor = ArgumentCaptor.forClass(MessageEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.MESSAGE), eq("conv-1"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().preview()).hasSize(100);
    }
}
