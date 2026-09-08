package com.socialapp.user.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.user.dto.FriendSuggestionResponse;
import com.socialapp.user.service.FriendSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/suggestions")
@RequiredArgsConstructor
public class FriendSuggestionController {

    private final FriendSuggestionService friendSuggestionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FriendSuggestionResponse>>> suggestions(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                friendSuggestionService.suggestions(CurrentUserContext.getUserId(), limit)));
    }

    @DeleteMapping("/{targetId}")
    public ResponseEntity<ApiResponse<Void>> dismiss(@PathVariable String targetId) {
        friendSuggestionService.dismiss(CurrentUserContext.getUserId(), targetId);
        return ResponseEntity.ok(ApiResponse.success("Suggestion dismissed", null));
    }
}
