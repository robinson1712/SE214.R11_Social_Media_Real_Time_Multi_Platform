package com.socialapp.search.dto;

/** Partial mirror of group-service's Group entity — unknown JSON fields are ignored by Jackson's default config. */
public record GroupSearchResult(String id, String name, String avatarUrl, String description) {
}
