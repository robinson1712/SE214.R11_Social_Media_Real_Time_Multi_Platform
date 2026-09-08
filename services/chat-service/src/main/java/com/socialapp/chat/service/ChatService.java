package com.socialapp.chat.service;

import com.socialapp.chat.document.Conversation;
import com.socialapp.chat.document.ConversationType;
import com.socialapp.chat.document.Message;
import com.socialapp.chat.repository.ConversationRepository;
import com.socialapp.chat.repository.MessageRepository;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.security.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final StringRedisTemplate redisTemplate;

    public Conversation createConversation(List<String> participantIds) {
        String currentUserId = CurrentUserContext.getUserId();
        Set<String> distinct = new LinkedHashSet<>();
        if (participantIds != null) {
            distinct.addAll(participantIds);
        }
        distinct.add(currentUserId);

        if (distinct.size() == 2) {
            Iterator<String> it = distinct.iterator();
            String a = it.next();
            String b = it.next();
            Optional<Conversation> existing = conversationRepository.findPrivateConversation(a, b);
            if (existing.isPresent()) {
                return existing.get();
            }
            Conversation conversation = Conversation.builder()
                    .type(ConversationType.PRIVATE)
                    .participantIds(new ArrayList<>(distinct))
                    .createdAt(Instant.now())
                    .build();
            return conversationRepository.save(conversation);
        }

        Conversation conversation = Conversation.builder()
                .type(ConversationType.GROUP)
                .participantIds(new ArrayList<>(distinct))
                .createdAt(Instant.now())
                .build();
        return conversationRepository.save(conversation);
    }

    public List<Conversation> getConversations(String userId) {
        List<Conversation> conversations = conversationRepository.findByParticipantIdsContaining(userId);
        conversations.sort(Comparator.comparing(
                (Conversation c) -> c.getLastMessageAt() != null ? c.getLastMessageAt() : c.getCreatedAt())
                .reversed());
        return conversations;
    }

    public Page<Message> getMessages(String conversationId, String userId, Pageable pageable) {
        Conversation conversation = getConversationOrThrow(conversationId);
        if (!conversation.getParticipantIds().contains(userId)) {
            throw new ForbiddenException("Not a participant of this conversation");
        }
        return messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable);
    }

    public void markRead(String conversationId, String userId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        if (!conversation.getParticipantIds().contains(userId)) {
            throw new ForbiddenException("Not a participant of this conversation");
        }
        List<Message> messages = messageRepository.findByConversationId(conversationId);
        for (Message message : messages) {
            if (message.getReadBy().add(userId)) {
                messageRepository.save(message);
            }
        }
    }

    public boolean isOnline(String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("presence:" + userId));
    }

    public Map<String, Boolean> onlineStatuses(List<String> userIds) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String userId : userIds) {
            result.put(userId, isOnline(userId));
        }
        return result;
    }

    public Conversation getConversationOrThrow(String id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + id));
    }

    /** Sender-only soft delete/recall — scrubs content so it can't be read back via the API. */
    public Message deleteMessage(String messageId, String userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));
        if (!message.getSenderId().equals(userId)) {
            throw new ForbiddenException("Only the sender may delete this message");
        }
        message.setDeleted(true);
        message.setContent(null);
        message.setMediaUrl(null);
        return messageRepository.save(message);
    }

    public void ensurePrivateConversation(String user1Id, String user2Id) {
        if (conversationRepository.findPrivateConversation(user1Id, user2Id).isEmpty()) {
            Conversation conversation = Conversation.builder()
                    .type(ConversationType.PRIVATE)
                    .participantIds(List.of(user1Id, user2Id))
                    .createdAt(Instant.now())
                    .build();
            conversationRepository.save(conversation);
        }
    }
}
