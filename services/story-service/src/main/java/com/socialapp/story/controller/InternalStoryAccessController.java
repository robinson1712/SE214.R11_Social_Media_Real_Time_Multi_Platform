package com.socialapp.story.controller;

import com.socialapp.common.dto.ContentAccessResponse;
import com.socialapp.story.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Internal API; no /api route is configured for it at the public gateway. */
@RestController
@RequestMapping("/internal/stories")
@RequiredArgsConstructor
public class InternalStoryAccessController {

    private final StoryService storyService;

    @GetMapping("/{id}/comment-access")
    public ContentAccessResponse commentAccess(@PathVariable String id, @RequestParam String viewerId) {
        return storyService.commentAccess(id, viewerId);
    }
}
