package com.socialapp.search.dto;

import java.time.Instant;

/** Partial mirror of post-service's Post entity — unknown JSON fields are ignored by Jackson's default config. */
public record PostSearchResult(String id, String authorId, String content, Instant createdAt) {
}
