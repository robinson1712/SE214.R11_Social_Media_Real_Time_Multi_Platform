package com.socialapp.search.client;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.search.dto.PageSearchResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "fanpage-service", fallbackFactory = FanpageServiceClientFallbackFactory.class)
public interface FanpageServiceClient {

    @GetMapping("/api/pages")
    ApiResponse<PageResponse<PageSearchResult>> search(
            @RequestParam("name") String name,
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}
