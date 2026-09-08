package com.socialapp.comment.event;

import com.socialapp.comment.service.CommentService;
import com.socialapp.common.event.ContentRemovedEvent;
import com.socialapp.common.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentModerationListener {

    private final CommentService commentService;

    @KafkaListener(topics = KafkaTopics.CONTENT_REMOVED, groupId = "${spring.kafka.consumer.group-id}")
    public void onContentRemoved(ContentRemovedEvent event) {
        if (!"COMMENT".equals(event.targetType())) {
            return;
        }
        log.info("Removing comment {} — moderation report {} ({})", event.targetId(), event.reportId(), event.reason());
        commentService.removeForModeration(event.targetId());
    }
}
