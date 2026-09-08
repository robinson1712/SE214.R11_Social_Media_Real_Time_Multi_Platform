package com.socialapp.common.event;

import java.time.Instant;

public record PageEvent(String pageId, String actorId, String type, Instant occurredAt) {
}
