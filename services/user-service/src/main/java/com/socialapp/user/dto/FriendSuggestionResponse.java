package com.socialapp.user.dto;

public record FriendSuggestionResponse(
        UserProfileResponse profile,
        int mutualFriendCount
) {
}
