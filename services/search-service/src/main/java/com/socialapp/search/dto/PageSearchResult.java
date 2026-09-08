package com.socialapp.search.dto;

/** Partial mirror of fanpage-service's Fanpage entity — unknown JSON fields are ignored by Jackson's default config. */
public record PageSearchResult(String id, String name, String avatarUrl, String category) {
}
