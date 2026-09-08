package com.socialapp.dating.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.dating.dto.CandidateResponse;
import com.socialapp.dating.dto.SwipeRequest;
import com.socialapp.dating.dto.SwipeResponse;
import com.socialapp.dating.dto.UpsertProfileRequest;
import com.socialapp.dating.entity.DatingProfile;
import com.socialapp.dating.entity.Match;
import com.socialapp.dating.service.DatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dating")
@RequiredArgsConstructor
public class DatingController {

    private final DatingService datingService;

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<DatingProfile>> upsertProfile(@Valid @RequestBody UpsertProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile saved", datingService.upsertProfile(request)));
    }

    @GetMapping("/profile/me")
    public ResponseEntity<ApiResponse<DatingProfile>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success(datingService.getMyProfile()));
    }

    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<PageResponse<CandidateResponse>>> getCandidates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(datingService.getCandidates(page, size))));
    }

    @PostMapping("/swipe")
    public ResponseEntity<ApiResponse<SwipeResponse>> swipe(@Valid @RequestBody SwipeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(datingService.swipe(request)));
    }

    @GetMapping("/matches")
    public ResponseEntity<ApiResponse<PageResponse<Match>>> getMatches(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(datingService.getMatches(pageable))));
    }

    @DeleteMapping("/matches/{id}")
    public ResponseEntity<ApiResponse<Void>> unmatch(@PathVariable String id) {
        datingService.unmatch(id);
        return ResponseEntity.ok(ApiResponse.success("Unmatched", null));
    }
}
