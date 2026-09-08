package com.socialapp.common.event;

import java.time.Instant;

public record PostCreatedEvent(String postId, String authorId, String groupId, String pageId,
                                String privacy, Instant occurredAt) {
}
