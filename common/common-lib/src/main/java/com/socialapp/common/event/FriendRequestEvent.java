package com.socialapp.common.event;

import java.time.Instant;

public record FriendRequestEvent(String requesterId, String targetUserId, String status, Instant occurredAt) {
}
