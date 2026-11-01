package com.socialapp.reaction.client;

import com.socialapp.common.dto.ContentAccessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "story-service")
public interface StoryAccessClient {
    @GetMapping("/internal/stories/{id}/comment-access")
    ContentAccessResponse check(@PathVariable("id") String id, @RequestParam("viewerId") String viewerId);
}
