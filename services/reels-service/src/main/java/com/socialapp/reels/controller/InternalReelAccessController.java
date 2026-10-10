package com.socialapp.reels.controller;

import com.socialapp.common.dto.ContentAccessResponse;
import com.socialapp.reels.service.ReelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Internal API; no /api route is configured for it at the public gateway. */
@RestController
@RequestMapping("/internal/reels")
@RequiredArgsConstructor
public class InternalReelAccessController {

    private final ReelService reelService;

    @GetMapping("/{id}/comment-access")
    public ContentAccessResponse commentAccess(@PathVariable String id, @RequestParam String viewerId) {
        return reelService.commentAccess(id);
    }
}
