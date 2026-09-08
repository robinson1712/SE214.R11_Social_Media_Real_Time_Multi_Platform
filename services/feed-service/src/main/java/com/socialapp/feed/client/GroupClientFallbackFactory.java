package com.socialapp.feed.client;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * If group-service is unreachable when a group post fans out, skip fanout for
 * this event rather than failing the whole listener — the post is still
 * readable directly from group-service, it just won't show up pre-fetched in
 * anyone's feed until the next fanout succeeds.
 */
@Slf4j
@Component
public class GroupClientFallbackFactory implements FallbackFactory<GroupClient> {

    @Override
    public GroupClient create(Throwable cause) {
        return (id, size) -> {
            log.warn("group-service unavailable while resolving members for group {}: {}", id, cause.getMessage());
            return ApiResponse.success(new PageResponse<>(List.of(), 0, size, 0, 0, true));
        };
    }
}
