package com.socialapp.fanpage.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.fanpage.dto.AddAdminRequest;
import com.socialapp.fanpage.dto.CreateFanpageRequest;
import com.socialapp.fanpage.entity.Fanpage;
import com.socialapp.fanpage.entity.PageAdmin;
import com.socialapp.fanpage.entity.PageFollower;
import com.socialapp.fanpage.service.FanpageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pages")
@RequiredArgsConstructor
public class FanpageController {

    private final FanpageService fanpageService;

    @PostMapping
    public ResponseEntity<ApiResponse<Fanpage>> create(@Valid @RequestBody CreateFanpageRequest request) {
        Fanpage page = fanpageService.createPage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Fanpage created", page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Fanpage>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(fanpageService.getPage(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<Fanpage>>> list(
            @RequestParam(required = false) String name,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(fanpageService.listPages(name, pageable))));
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<ApiResponse<PageFollower>> follow(@PathVariable String id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Now following page", fanpageService.follow(id)));
    }

    @DeleteMapping("/{id}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollow(@PathVariable String id) {
        fanpageService.unfollow(id);
        return ResponseEntity.ok(ApiResponse.success("Unfollowed page", null));
    }

    @PostMapping("/{id}/admins")
    public ResponseEntity<ApiResponse<PageAdmin>> addAdmin(@PathVariable String id, @Valid @RequestBody AddAdminRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Admin added", fanpageService.addOrUpdateAdmin(id, request)));
    }

    @DeleteMapping("/{id}/admins/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeAdmin(@PathVariable String id, @PathVariable String userId) {
        fanpageService.removeAdmin(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Admin removed", null));
    }

    @GetMapping("/{id}/followers")
    public ResponseEntity<ApiResponse<PageResponse<PageFollower>>> followers(
            @PathVariable String id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(fanpageService.listFollowers(id, pageable))));
    }

    @GetMapping("/me/managed")
    public ResponseEntity<ApiResponse<PageResponse<Fanpage>>> myManagedPages(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(fanpageService.myManagedPages(pageable))));
    }

    @GetMapping("/me/followed")
    public ResponseEntity<ApiResponse<PageResponse<Fanpage>>> myFollowedPages(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(fanpageService.myFollowedPages(pageable))));
    }
}
