package com.socialapp.post.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "fanpage-service")
public interface FanpageAuthorizationClient {

    @GetMapping("/internal/pages/{pageId}/managers/{userId}/can-post")
    boolean canManagePosts(@PathVariable("pageId") String pageId, @PathVariable("userId") String userId);
}
