package com.socialapp.reaction.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.common.enums.TargetType;
import com.socialapp.reaction.dto.SaveItemRequest;
import com.socialapp.reaction.entity.SavedItem;
import com.socialapp.reaction.service.SavedItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/saved")
@RequiredArgsConstructor
public class SavedItemController {

    private final SavedItemService savedItemService;

    @PutMapping
    public ResponseEntity<ApiResponse<SavedItem>> save(@RequestBody SaveItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Saved", savedItemService.save(request)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unsave(
            @RequestParam TargetType targetType,
            @RequestParam String targetId) {
        savedItemService.unsave(targetType, targetId);
        return ResponseEntity.ok(ApiResponse.success("Unsaved", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SavedItem>> mine(
            @RequestParam TargetType targetType,
            @RequestParam String targetId) {
        return ResponseEntity.ok(ApiResponse.success(savedItemService.getMine(targetType, targetId).orElse(null)));
    }

    /** All of the current user's saved items across every target type,
     * newest first — the "Đã lưu" page renders each row by fetching the
     * underlying post/reel by (targetType, targetId), same pattern PostCard
     * already uses to render a shared post's preview. */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResponse<SavedItem>>> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(savedItemService.listMine(pageable))));
    }
}
