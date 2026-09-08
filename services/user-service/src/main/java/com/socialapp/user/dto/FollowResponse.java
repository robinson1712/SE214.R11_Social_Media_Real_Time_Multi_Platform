package com.socialapp.user.dto;

import com.socialapp.user.entity.Follow;

import java.time.Instant;

public record FollowResponse(
        String id,
        String followerId,
        String followeeId,
        Instant createdAt
) {
    public static FollowResponse from(Follow f) {
        return new FollowResponse(f.getId(), f.getFollowerId(), f.getFolloweeId(), f.getCreatedAt());
    }
}
