package com.socialapp.common.event;

import java.time.Instant;

public record ReactionEvent(String reactionId, String targetType, String targetId, String targetOwnerId,
                             String userId, String reactionType, boolean removed, Instant occurredAt) {
}
