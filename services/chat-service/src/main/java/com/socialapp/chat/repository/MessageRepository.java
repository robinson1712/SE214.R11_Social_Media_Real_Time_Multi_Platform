package com.socialapp.chat.repository;

import com.socialapp.chat.document.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {

    Page<Message> findByConversationIdOrderBySentAtDesc(String conversationId, Pageable pageable);

    List<Message> findByConversationId(String conversationId);
}
