package com.socialapp.feed.client;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.feed.dto.PostDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "post-service", fallbackFactory = PostClientFallbackFactory.class)
public interface PostClient {

    @GetMapping("/api/posts/batch")
    ApiResponse<List<PostDto>> getBatch(@RequestParam("ids") String ids);
}
