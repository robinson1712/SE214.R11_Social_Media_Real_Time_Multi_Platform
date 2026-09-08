package com.socialapp.search.client;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.search.dto.PostSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class PostServiceClientFallbackFactory implements FallbackFactory<PostServiceClient> {

    @Override
    public PostServiceClient create(Throwable cause) {
        return (q, page, size) -> {
            log.warn("post-service unavailable while searching for '{}': {}", q, cause.getMessage());
            return ApiResponse.success(new PageResponse<>(List.of(), page, size, 0, 0, true));
        };
    }
}
