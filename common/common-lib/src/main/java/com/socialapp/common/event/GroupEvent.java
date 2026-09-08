package com.socialapp.common.event;

import java.time.Instant;

public record GroupEvent(String groupId, String actorId, String targetUserId, String type, Instant occurredAt) {
}
