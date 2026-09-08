package com.socialapp.common.event;

import java.time.Instant;

public record CommentCreatedEvent(String commentId, String targetType, String targetId, String authorId,
                                   String targetOwnerId, String parentCommentId, Instant occurredAt) {
}
