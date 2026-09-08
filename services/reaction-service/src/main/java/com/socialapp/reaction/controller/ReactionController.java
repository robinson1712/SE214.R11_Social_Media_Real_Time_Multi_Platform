package com.socialapp.reaction.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.enums.TargetType;
import com.socialapp.reaction.dto.ReactionSummaryResponse;
import com.socialapp.reaction.dto.UpsertReactionRequest;
import com.socialapp.reaction.entity.Reaction;
import com.socialapp.reaction.service.ReactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @PutMapping
    public ResponseEntity<ApiResponse<Reaction>> upsert(@RequestBody UpsertReactionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Reaction saved", reactionService.upsert(request)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestParam TargetType targetType,
            @RequestParam String targetId) {
        reactionService.delete(targetType, targetId);
        return ResponseEntity.ok(ApiResponse.success("Reaction removed", null));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ReactionSummaryResponse>> summary(
            @RequestParam TargetType targetType,
            @RequestParam String targetId) {
        return ResponseEntity.ok(ApiResponse.success(reactionService.getSummary(targetType, targetId)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Reaction>> myReaction(
            @RequestParam TargetType targetType,
            @RequestParam String targetId) {
        return ResponseEntity.ok(ApiResponse.success(reactionService.getMyReaction(targetType, targetId).orElse(null)));
    }
}
