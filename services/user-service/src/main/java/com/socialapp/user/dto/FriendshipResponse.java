package com.socialapp.user.dto;

import com.socialapp.user.entity.Friendship;
import com.socialapp.user.entity.FriendshipStatus;

import java.time.Instant;

public record FriendshipResponse(
        String id,
        String requesterId,
        String addresseeId,
        FriendshipStatus status,
        Instant createdAt,
        Instant respondedAt
) {
    public static FriendshipResponse from(Friendship f) {
        return new FriendshipResponse(f.getId(), f.getRequesterId(), f.getAddresseeId(), f.getStatus(),
                f.getCreatedAt(), f.getRespondedAt());
    }
}
