package com.socialapp.user.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.user.dto.MediaUrlRequest;
import com.socialapp.user.dto.UpdateProfileRequest;
import com.socialapp.user.dto.UserProfileResponse;
import com.socialapp.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getProfile(id)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<UserProfileResponse>>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(userProfileService.search(q, pageable))));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe() {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getMe(CurrentUserContext.getUserId())));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMe(@RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated",
                userProfileService.updateMe(CurrentUserContext.getUserId(), request)));
    }

    @PutMapping("/me/avatar")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateAvatar(@Valid @RequestBody MediaUrlRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Avatar updated",
                userProfileService.updateAvatar(CurrentUserContext.getUserId(), request.mediaUrl())));
    }

    @PutMapping("/me/cover")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateCover(@Valid @RequestBody MediaUrlRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cover updated",
                userProfileService.updateCover(CurrentUserContext.getUserId(), request.mediaUrl())));
    }
}
