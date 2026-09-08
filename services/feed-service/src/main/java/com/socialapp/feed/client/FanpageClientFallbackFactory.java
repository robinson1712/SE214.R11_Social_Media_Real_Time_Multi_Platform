package com.socialapp.feed.client;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Same reasoning as GroupClientFallbackFactory: a page post that can't fan out
 * right now is still readable directly from fanpage-service.
 */
@Slf4j
@Component
public class FanpageClientFallbackFactory implements FallbackFactory<FanpageClient> {

    @Override
    public FanpageClient create(Throwable cause) {
        return (id, size) -> {
            log.warn("fanpage-service unavailable while resolving followers for page {}: {}", id, cause.getMessage());
            return ApiResponse.success(new PageResponse<>(List.of(), 0, size, 0, 0, true));
        };
    }
}
