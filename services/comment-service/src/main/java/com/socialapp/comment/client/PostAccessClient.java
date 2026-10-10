package com.socialapp.comment.client;

import com.socialapp.common.dto.ContentAccessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "post-service")
public interface PostAccessClient {
    @GetMapping("/internal/posts/{id}/comment-access")
    ContentAccessResponse check(@PathVariable("id") String id, @RequestParam("viewerId") String viewerId);
}
