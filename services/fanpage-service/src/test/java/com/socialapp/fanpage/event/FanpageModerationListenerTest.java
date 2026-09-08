package com.socialapp.fanpage.event;

import com.socialapp.common.event.ContentRemovedEvent;
import com.socialapp.fanpage.service.FanpageService;
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
 * Unit tests for FanpageModerationListener — the @KafkaListener method is
 * invoked directly; no real Kafka broker is involved.
 */
@ExtendWith(MockitoExtension.class)
class FanpageModerationListenerTest {

    @Mock
    private FanpageService fanpageService;

    private FanpageModerationListener listener;

    @BeforeEach
    void setUp() {
        listener = new FanpageModerationListener(fanpageService);
    }

    @Test
    void onContentRemoved_forFanpageTarget_delegatesToFanpageService() {
        ContentRemovedEvent event = new ContentRemovedEvent("FANPAGE", "page-1", "report-1", "SPAM", Instant.now());

        listener.onContentRemoved(event);

        verify(fanpageService).removeForModeration("page-1");
    }

    @Test
    void onContentRemoved_forNonFanpageTarget_isIgnored() {
        ContentRemovedEvent event = new ContentRemovedEvent("POST", "post-1", "report-1", "SPAM", Instant.now());

        listener.onContentRemoved(event);

        verify(fanpageService, never()).removeForModeration(any());
    }
}
