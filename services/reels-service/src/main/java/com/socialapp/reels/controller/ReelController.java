package com.socialapp.reels.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.reels.document.Reel;
import com.socialapp.reels.dto.CreateReelRequest;
import com.socialapp.reels.service.ReelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reels")
@RequiredArgsConstructor
public class ReelController {

    private final ReelService reelService;

    @PostMapping
    public ResponseEntity<ApiResponse<Reel>> createReel(@Valid @RequestBody CreateReelRequest request) {
        Reel reel = reelService.createReel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Reel created", reel));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Reel>> getReel(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(reelService.getReel(id)));
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<ApiResponse<Reel>> viewReel(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(reelService.viewReel(id)));
    }

    @PostMapping("/{id}/share-count")
    public ResponseEntity<ApiResponse<Reel>> incrementShareCount(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(reelService.incrementShareCount(id)));
    }

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<PageResponse<Reel>>> getFeed(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Reel> page = reelService.getFeed(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(page)));
    }

    @GetMapping("/author/{authorId}")
    public ResponseEntity<ApiResponse<PageResponse<Reel>>> getReelsByAuthor(
            @PathVariable String authorId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Reel> page = reelService.getReelsByAuthor(authorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(page)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReel(@PathVariable String id) {
        reelService.deleteReel(id);
        return ResponseEntity.ok(ApiResponse.success("Reel deleted", null));
    }
}
