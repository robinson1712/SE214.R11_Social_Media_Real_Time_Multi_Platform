package com.socialapp.chat.repository;

import com.socialapp.chat.document.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    List<Conversation> findByParticipantIdsContaining(String userId);

    @Query("{ 'type': 'PRIVATE', 'participantIds': { $all: [?0, ?1], $size: 2 } }")
    Optional<Conversation> findPrivateConversation(String user1Id, String user2Id);
}
