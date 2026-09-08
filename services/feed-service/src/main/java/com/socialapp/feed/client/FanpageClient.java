package com.socialapp.feed.client;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.feed.dto.PageFollowerDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "fanpage-service", fallbackFactory = FanpageClientFallbackFactory.class)
public interface FanpageClient {

    @GetMapping("/api/pages/{id}/followers")
    ApiResponse<PageResponse<PageFollowerDto>> getFollowers(@PathVariable("id") String id,
                                                              @RequestParam("size") int size);
}
