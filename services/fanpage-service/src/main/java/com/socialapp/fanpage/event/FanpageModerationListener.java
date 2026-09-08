package com.socialapp.fanpage.event;

import com.socialapp.common.event.ContentRemovedEvent;
import com.socialapp.common.event.KafkaTopics;
import com.socialapp.fanpage.service.FanpageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FanpageModerationListener {

    private final FanpageService fanpageService;

    @KafkaListener(topics = KafkaTopics.CONTENT_REMOVED, groupId = "${spring.kafka.consumer.group-id}")
    public void onContentRemoved(ContentRemovedEvent event) {
        if (!"FANPAGE".equals(event.targetType())) {
            return;
        }
        log.info("Removing fanpage {} — moderation report {} ({})", event.targetId(), event.reportId(), event.reason());
        fanpageService.removeForModeration(event.targetId());
    }
}
