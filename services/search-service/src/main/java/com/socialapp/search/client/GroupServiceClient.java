package com.socialapp.search.client;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.search.dto.GroupSearchResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "group-service", fallbackFactory = GroupServiceClientFallbackFactory.class)
public interface GroupServiceClient {

    @GetMapping("/api/groups")
    ApiResponse<PageResponse<GroupSearchResult>> search(
            @RequestParam("name") String name,
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}
