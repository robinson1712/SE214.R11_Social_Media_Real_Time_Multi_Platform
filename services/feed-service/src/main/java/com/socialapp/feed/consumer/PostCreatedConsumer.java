package com.socialapp.feed.consumer;

import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.PostCreatedEvent;
import com.socialapp.feed.service.FeedFanoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostCreatedConsumer {

    private final FeedFanoutService feedFanoutService;

    @KafkaListener(topics = KafkaTopics.POST_CREATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onPostCreated(PostCreatedEvent event) {
        log.info("Received PostCreatedEvent for post {}", event.postId());
        try {
            feedFanoutService.fanout(event);
        } catch (Exception e) {
            log.warn("Failed to fanout post {}: {}", event.postId(), e.getMessage());
        }
    }
}
