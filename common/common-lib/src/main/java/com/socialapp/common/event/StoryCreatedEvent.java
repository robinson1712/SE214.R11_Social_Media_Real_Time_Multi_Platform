package com.socialapp.common.event;

import java.time.Instant;

public record StoryCreatedEvent(String storyId, String authorId, Instant occurredAt) {
}
