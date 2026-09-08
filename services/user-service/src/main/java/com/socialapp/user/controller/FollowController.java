package com.socialapp.user.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.user.dto.FollowStatusResponse;
import com.socialapp.user.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{targetId}/follow")
    public ResponseEntity<ApiResponse<Void>> follow(@PathVariable String targetId) {
        followService.follow(CurrentUserContext.getUserId(), targetId);
        return ResponseEntity.ok(ApiResponse.success("Followed", null));
    }

    @DeleteMapping("/{targetId}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollow(@PathVariable String targetId) {
        followService.unfollow(CurrentUserContext.getUserId(), targetId);
        return ResponseEntity.ok(ApiResponse.success("Unfollowed", null));
    }

    @GetMapping("/{targetId}/follow-status")
    public ResponseEntity<ApiResponse<FollowStatusResponse>> status(@PathVariable String targetId) {
        return ResponseEntity.ok(ApiResponse.success(followService.status(CurrentUserContext.getUserId(), targetId)));
    }
}
