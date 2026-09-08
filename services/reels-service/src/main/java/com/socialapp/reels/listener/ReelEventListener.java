package com.socialapp.reels.listener;

import com.socialapp.common.event.CommentCreatedEvent;
import com.socialapp.common.event.ContentRemovedEvent;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.ReactionEvent;
import com.socialapp.reels.repository.ReelRepository;
import com.socialapp.reels.service.ReelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReelEventListener {

    private static final String REEL_TARGET_TYPE = "REEL";

    private final ReelRepository reelRepository;
    private final ReelService reelService;

    @KafkaListener(topics = KafkaTopics.COMMENT_CREATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onCommentCreated(CommentCreatedEvent event) {
        if (!REEL_TARGET_TYPE.equals(event.targetType()) || event.parentCommentId() != null) {
            return;
        }
        reelRepository.findById(event.targetId()).ifPresent(reel -> {
            reel.setCommentCount(reel.getCommentCount() + 1);
            reelRepository.save(reel);
        });
    }

    @KafkaListener(topics = KafkaTopics.REACTION, groupId = "${spring.kafka.consumer.group-id}")
    public void onReaction(ReactionEvent event) {
        if (!REEL_TARGET_TYPE.equals(event.targetType())) {
            return;
        }
        reelRepository.findById(event.targetId()).ifPresent(reel -> {
            if (event.removed()) {
                reel.setReactionCount(Math.max(0, reel.getReactionCount() - 1));
            } else {
                reel.setReactionCount(reel.getReactionCount() + 1);
            }
            reelRepository.save(reel);
        });
    }

    @KafkaListener(topics = KafkaTopics.CONTENT_REMOVED, groupId = "${spring.kafka.consumer.group-id}")
    public void onContentRemoved(ContentRemovedEvent event) {
        if (!REEL_TARGET_TYPE.equals(event.targetType())) {
            return;
        }
        log.info("Removing reel {} — moderation report {} ({})", event.targetId(), event.reportId(), event.reason());
        reelService.removeForModeration(event.targetId());
    }
}
