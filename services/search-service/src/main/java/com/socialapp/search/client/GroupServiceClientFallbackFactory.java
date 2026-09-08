package com.socialapp.search.client;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.search.dto.GroupSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class GroupServiceClientFallbackFactory implements FallbackFactory<GroupServiceClient> {

    @Override
    public GroupServiceClient create(Throwable cause) {
        return (name, page, size) -> {
            log.warn("group-service unavailable while searching for '{}': {}", name, cause.getMessage());
            return ApiResponse.success(new PageResponse<>(List.of(), page, size, 0, 0, true));
        };
    }
}
