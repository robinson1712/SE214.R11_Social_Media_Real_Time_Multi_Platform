package com.socialapp.group.event;

import com.socialapp.common.event.ContentRemovedEvent;
import com.socialapp.group.service.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for GroupModerationListener — the @KafkaListener method is
 * invoked directly; no real Kafka broker is involved.
 */
@ExtendWith(MockitoExtension.class)
class GroupModerationListenerTest {

    @Mock
    private GroupService groupService;

    private GroupModerationListener listener;

    @BeforeEach
    void setUp() {
        listener = new GroupModerationListener(groupService);
    }

    @Test
    void onContentRemoved_forGroupTarget_delegatesToGroupService() {
        ContentRemovedEvent event = new ContentRemovedEvent("GROUP", "group-1", "report-1", "SPAM", Instant.now());

        listener.onContentRemoved(event);

        verify(groupService).removeForModeration("group-1");
    }

    @Test
    void onContentRemoved_forNonGroupTarget_isIgnored() {
        ContentRemovedEvent event = new ContentRemovedEvent("POST", "post-1", "report-1", "SPAM", Instant.now());

        listener.onContentRemoved(event);

        verify(groupService, never()).removeForModeration(any());
    }
}
