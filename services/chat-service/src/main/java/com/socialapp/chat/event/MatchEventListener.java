package com.socialapp.chat.event;

import com.socialapp.chat.service.ChatService;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.MatchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchEventListener {

    private final ChatService chatService;

    @KafkaListener(topics = KafkaTopics.MATCH, groupId = "${spring.kafka.consumer.group-id}")
    public void onMatch(MatchEvent event) {
        log.info("Received match event {}, ensuring private conversation", event.matchId());
        chatService.ensurePrivateConversation(event.user1Id(), event.user2Id());
    }
}
