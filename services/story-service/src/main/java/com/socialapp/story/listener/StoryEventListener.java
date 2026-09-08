package com.socialapp.story.listener;

import com.socialapp.common.event.ContentRemovedEvent;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.story.service.StoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoryEventListener {

    private final StoryService storyService;

    @KafkaListener(topics = KafkaTopics.CONTENT_REMOVED, groupId = "${spring.kafka.consumer.group-id}")
    public void onContentRemoved(ContentRemovedEvent event) {
        if (!"STORY".equals(event.targetType())) {
            return;
        }
        log.info("Removing story {} — moderation report {} ({})", event.targetId(), event.reportId(), event.reason());
        storyService.removeForModeration(event.targetId());
    }
}
