package com.socialapp.feed.client;

import com.socialapp.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * If post-service is unreachable while hydrating a feed page, return an empty
 * batch rather than a 500 — FeedQueryService already tolerates ids that don't
 * resolve to a post (it just filters them out), so the caller gets a
 * temporarily short feed instead of an error.
 */
@Slf4j
@Component
public class PostClientFallbackFactory implements FallbackFactory<PostClient> {

    @Override
    public PostClient create(Throwable cause) {
        return ids -> {
            log.warn("post-service unavailable while hydrating posts [{}]: {}", ids, cause.getMessage());
            return ApiResponse.success(List.of());
        };
    }
}
