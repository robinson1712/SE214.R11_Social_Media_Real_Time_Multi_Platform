package com.socialapp.fanpage.controller;

import com.socialapp.fanpage.service.FanpageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Private service-to-service authorization API. It intentionally lives outside
 * the public /api/pages route so the API gateway does not expose it.
 */
@RestController
@RequestMapping("/internal/pages")
@RequiredArgsConstructor
public class InternalFanpageAuthorizationController {

    private final FanpageService fanpageService;

    @GetMapping("/{pageId}/managers/{userId}/can-post")
    public boolean canManagePosts(@PathVariable String pageId, @PathVariable String userId) {
        return fanpageService.canCreatePost(pageId, userId);
    }
}
