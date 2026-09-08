package com.socialapp.feed.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PostDto(
        String id,
        String authorId,
        String content,
        List<String> mediaUrls,
        String privacy,
        List<String> taggedUserIds,
        String groupId,
        String pageId,
        String sharedPostId,
        long commentCount,
        long reactionCount,
        boolean pinned,
        long shareCount,
        Instant createdAt
) {
}
