package com.socialapp.common.event;

import java.time.Instant;

public record ReelCreatedEvent(String reelId, String authorId, Instant occurredAt) {
}
