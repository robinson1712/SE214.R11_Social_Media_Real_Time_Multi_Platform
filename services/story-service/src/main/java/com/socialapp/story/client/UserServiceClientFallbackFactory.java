package com.socialapp.story.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * If user-service is unreachable, degrade the story feed gracefully to just
 * the caller's own stories rather than failing the whole request — losing the
 * friends list for a moment is a much smaller problem than a 500 error.
 */
@Slf4j
@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        return id -> {
            log.warn("user-service unavailable while resolving friend-ids for {}: {}", id, cause.getMessage());
            return Collections.emptyList();
        };
    }
}
