package com.socialapp.post.event;

import com.socialapp.common.enums.TargetType;
import com.socialapp.common.event.CommentCreatedEvent;
import com.socialapp.common.event.ContentRemovedEvent;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.ReactionEvent;
import com.socialapp.post.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostEventListener {

    private final PostService postService;

    @KafkaListener(topics = KafkaTopics.COMMENT_CREATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onCommentCreated(CommentCreatedEvent event) {
        if (!TargetType.POST.name().equals(event.targetType())) {
            return;
        }
        if (event.parentCommentId() == null) {
            postService.incrementCommentCount(event.targetId());
        }
    }

    @KafkaListener(topics = KafkaTopics.REACTION, groupId = "${spring.kafka.consumer.group-id}")
    public void onReaction(ReactionEvent event) {
        if (!TargetType.POST.name().equals(event.targetType())) {
            return;
        }
        postService.applyReactionDelta(event.targetId(), event.removed());
    }

    @KafkaListener(topics = KafkaTopics.CONTENT_REMOVED, groupId = "${spring.kafka.consumer.group-id}")
    public void onContentRemoved(ContentRemovedEvent event) {
        if (!"POST".equals(event.targetType())) {
            return;
        }
        log.info("Removing post {} — moderation report {} ({})", event.targetId(), event.reportId(), event.reason());
        postService.removeForModeration(event.targetId());
    }
}
