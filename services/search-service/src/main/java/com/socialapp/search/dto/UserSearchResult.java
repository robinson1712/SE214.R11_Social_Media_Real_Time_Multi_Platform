package com.socialapp.search.dto;

/** Partial mirror of user-service's UserProfileResponse — unknown JSON fields are ignored by Jackson's default config. */
public record UserSearchResult(String id, String fullName, String avatarUrl) {
}
