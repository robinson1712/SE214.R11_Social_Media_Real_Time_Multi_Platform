package com.socialapp.group.event;

import com.socialapp.common.event.ContentRemovedEvent;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.group.service.GroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupModerationListener {

    private final GroupService groupService;

    @KafkaListener(topics = KafkaTopics.CONTENT_REMOVED, groupId = "${spring.kafka.consumer.group-id}")
    public void onContentRemoved(ContentRemovedEvent event) {
        if (!"GROUP".equals(event.targetType())) {
            return;
        }
        log.info("Removing group {} — moderation report {} ({})", event.targetId(), event.reportId(), event.reason());
        groupService.removeForModeration(event.targetId());
    }
}
