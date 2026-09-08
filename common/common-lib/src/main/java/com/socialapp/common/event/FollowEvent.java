package com.socialapp.common.event;

import java.time.Instant;

/** Published only on follow (not unfollow) — see FollowService for why. */
public record FollowEvent(String followerId, String followeeId, Instant occurredAt) {
}
