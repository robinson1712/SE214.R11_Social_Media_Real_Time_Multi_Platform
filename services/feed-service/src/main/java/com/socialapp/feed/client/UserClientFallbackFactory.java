package com.socialapp.feed.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * If user-service is unreachable, a personal post simply doesn't fan out to
 * friends this time — it still lands in the author's own feed. Degrading to an
 * empty friend list is strictly better than failing the whole Kafka listener
 * (which would otherwise retry/redeliver the same event repeatedly).
 */
@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    @Override
    public UserClient create(Throwable cause) {
        return id -> {
            log.warn("user-service unavailable while resolving friend-ids for {}: {}", id, cause.getMessage());
            return Collections.emptyList();
        };
    }
}
