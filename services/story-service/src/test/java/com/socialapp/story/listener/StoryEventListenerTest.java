package com.socialapp.story.listener;

import com.socialapp.common.event.ContentRemovedEvent;
import com.socialapp.story.service.StoryService;
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
 * Unit tests for StoryEventListener — the @KafkaListener method is invoked
 * directly; no real Kafka broker is involved.
 */
@ExtendWith(MockitoExtension.class)
class StoryEventListenerTest {

    @Mock
    private StoryService storyService;

    private StoryEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new StoryEventListener(storyService);
    }

    @Test
    void onContentRemoved_forStoryTarget_delegatesToStoryService() {
        ContentRemovedEvent event = new ContentRemovedEvent("STORY", "story-1", "report-1", "SPAM", Instant.now());

        listener.onContentRemoved(event);

        verify(storyService).removeForModeration("story-1");
    }

    @Test
    void onContentRemoved_forNonStoryTarget_isIgnored() {
        ContentRemovedEvent event = new ContentRemovedEvent("POST", "post-1", "report-1", "SPAM", Instant.now());

        listener.onContentRemoved(event);

        verify(storyService, never()).removeForModeration(any());
    }
}
