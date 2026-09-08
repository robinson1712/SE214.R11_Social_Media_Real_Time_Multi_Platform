package com.socialapp.search.client;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.search.dto.UserSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * If user-service is unreachable, degrade to an empty user-results section
 * rather than failing the whole search — a search missing one category is a
 * much smaller problem than a 500 error for the entire query.
 */
@Slf4j
@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        return (q, page, size) -> {
            log.warn("user-service unavailable while searching for '{}': {}", q, cause.getMessage());
            return ApiResponse.success(new PageResponse<>(List.of(), page, size, 0, 0, true));
        };
    }
}
