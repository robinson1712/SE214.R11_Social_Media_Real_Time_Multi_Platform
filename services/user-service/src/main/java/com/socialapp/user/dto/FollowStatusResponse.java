package com.socialapp.user.dto;

public record FollowStatusResponse(boolean following, long followerCount, long followingCount) {
}
