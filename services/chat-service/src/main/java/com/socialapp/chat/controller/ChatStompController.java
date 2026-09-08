package com.socialapp.chat.controller;

import com.socialapp.chat.document.Conversation;
import com.socialapp.chat.document.ConversationType;
import com.socialapp.chat.document.Message;
import com.socialapp.chat.dto.ChatSendRequest;
import com.socialapp.chat.repository.ConversationRepository;
import com.socialapp.chat.repository.MessageRepository;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.MessageEvent;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private static final int PREVIEW_MAX_LENGTH = 100;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(ChatSendRequest request, Principal principal) {
        String senderId = principal.getName();

        Conversation conversation = conversationRepository.findById(request.conversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + request.conversationId()));

        if (!conversation.getParticipantIds().contains(senderId)) {
            throw new ForbiddenException("Sender is not a participant of this conversation");
        }

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(senderId)
                .content(request.content())
                .mediaUrl(request.mediaUrl())
                .storyReplyId(request.storyReplyId())
                .storyReplyPreviewUrl(request.storyReplyPreviewUrl())
                .sentAt(Instant.now())
                .build();
        Message saved = messageRepository.save(message);

        String preview = request.content() == null || request.content().isBlank()
                ? (request.mediaUrl() != null ? "Đã gửi tệp đính kèm" : null)
                : truncate(request.content());
        conversation.setLastMessagePreview(preview);
        conversation.setLastMessageAt(saved.getSentAt());
        conversationRepository.save(conversation);

        String recipientId = null;
        if (conversation.getType() == ConversationType.PRIVATE) {
            recipientId = conversation.getParticipantIds().stream()
                    .filter(id -> !id.equals(senderId))
                    .findFirst()
                    .orElse(null);
        }

        MessageEvent event = new MessageEvent(saved.getId(), conversation.getId(), senderId, recipientId, preview, Instant.now());
        kafkaTemplate.send(KafkaTopics.MESSAGE, conversation.getId(), event);

        for (String participantId : conversation.getParticipantIds()) {
            messagingTemplate.convertAndSendToUser(participantId, "/queue/messages", saved);
        }
    }

    private String truncate(String content) {
        if (content == null) {
            return null;
        }
        return content.length() > PREVIEW_MAX_LENGTH ? content.substring(0, PREVIEW_MAX_LENGTH) : content;
    }
}
