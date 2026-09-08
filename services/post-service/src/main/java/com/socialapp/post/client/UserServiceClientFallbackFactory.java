package com.socialapp.post.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * If user-service is unreachable, degrade FRIENDS-privacy visibility checks to
 * "no friends visible" (fail closed) rather than throwing — a post staying
 * hidden a moment longer is a much smaller problem than a 500 error, and
 * failing closed is the right default for a privacy check specifically.
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
