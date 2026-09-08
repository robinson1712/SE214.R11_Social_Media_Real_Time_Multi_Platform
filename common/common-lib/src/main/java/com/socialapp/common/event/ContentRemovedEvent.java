package com.socialapp.common.event;

import java.time.Instant;

/**
 * Published by moderation-service when an admin resolves a report with the
 * REMOVE_CONTENT action. Every content-owning service (post/comment/reels/
 * story) listens for this and only acts when targetType matches what it owns.
 */
public record ContentRemovedEvent(String targetType, String targetId, String reportId, String reason, Instant occurredAt) {
}
