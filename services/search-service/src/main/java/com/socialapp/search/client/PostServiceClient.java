package com.socialapp.search.client;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.search.dto.PostSearchResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "post-service", fallbackFactory = PostServiceClientFallbackFactory.class)
public interface PostServiceClient {

    @GetMapping("/api/posts/search")
    ApiResponse<PageResponse<PostSearchResult>> search(
            @RequestParam("q") String q,
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}
