package com.socialapp.common.event;

import java.time.Instant;

/** One event per tagged user, mirroring FriendRequestEvent's one-event-one-recipient shape. */
public record PostTaggedEvent(String postId, String authorId, String taggedUserId, Instant occurredAt) {
}
