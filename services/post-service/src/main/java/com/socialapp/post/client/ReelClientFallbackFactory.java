package com.socialapp.post.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * If reels-service is unreachable, the share itself (a new Post row) has
 * already been created — losing the reel's share-count bump is a cosmetic
 * miss, not a reason to fail the whole request, so this degrades to a no-op
 * rather than throwing.
 */
@Slf4j
@Component
public class ReelClientFallbackFactory implements FallbackFactory<ReelClient> {

    @Override
    public ReelClient create(Throwable cause) {
        return id -> log.warn("reels-service unavailable while bumping share count for reel {}: {}", id, cause.getMessage());
    }
}
