package com.socialapp.common.event;

import java.time.Instant;

public record MatchEvent(String matchId, String user1Id, String user2Id, Instant occurredAt) {
}
