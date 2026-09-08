package com.socialapp.user.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.common.security.CurrentUserContext;
import com.socialapp.user.dto.FriendshipResponse;
import com.socialapp.user.dto.FriendshipStatusResponse;
import com.socialapp.user.dto.UserProfileResponse;
import com.socialapp.user.service.FriendshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @PostMapping("/{targetId}/friend-request")
    public ResponseEntity<ApiResponse<FriendshipResponse>> sendFriendRequest(@PathVariable String targetId) {
        return ResponseEntity.ok(ApiResponse.success("Friend request sent",
                friendshipService.sendFriendRequest(CurrentUserContext.getUserId(), targetId)));
    }

    @PutMapping("/friend-requests/{friendshipId}/accept")
    public ResponseEntity<ApiResponse<FriendshipResponse>> accept(@PathVariable String friendshipId) {
        return ResponseEntity.ok(ApiResponse.success("Friend request accepted",
                friendshipService.accept(CurrentUserContext.getUserId(), friendshipId)));
    }

    @PutMapping("/friend-requests/{friendshipId}/decline")
    public ResponseEntity<ApiResponse<FriendshipResponse>> decline(@PathVariable String friendshipId) {
        return ResponseEntity.ok(ApiResponse.success("Friend request declined",
                friendshipService.decline(CurrentUserContext.getUserId(), friendshipId)));
    }

    @DeleteMapping("/friends/{friendId}")
    public ResponseEntity<ApiResponse<Void>> removeFriend(@PathVariable String friendId) {
        friendshipService.removeFriend(CurrentUserContext.getUserId(), friendId);
        return ResponseEntity.ok(ApiResponse.success("Friend removed", null));
    }

    @GetMapping("/me/friends")
    public ResponseEntity<ApiResponse<PageResponse<UserProfileResponse>>> listFriends(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                friendshipService.listFriends(CurrentUserContext.getUserId(), pageable)));
    }

    @GetMapping("/me/friend-requests")
    public ResponseEntity<ApiResponse<PageResponse<FriendshipResponse>>> listFriendRequests(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                friendshipService.listFriendRequests(CurrentUserContext.getUserId(), pageable)));
    }

    @GetMapping("/{targetId}/friendship-status")
    public ResponseEntity<ApiResponse<FriendshipStatusResponse>> friendshipStatus(@PathVariable String targetId) {
        return ResponseEntity.ok(ApiResponse.success(
                friendshipService.relationshipStatus(CurrentUserContext.getUserId(), targetId)));
    }

    @GetMapping("/{id}/friend-ids")
    public ResponseEntity<List<String>> friendIds(@PathVariable String id) {
        return ResponseEntity.ok(friendshipService.friendIds(id));
    }

    @PostMapping("/{targetId}/block")
    public ResponseEntity<ApiResponse<Void>> block(@PathVariable String targetId) {
        friendshipService.block(CurrentUserContext.getUserId(), targetId);
        return ResponseEntity.ok(ApiResponse.success("User blocked", null));
    }

    @DeleteMapping("/{targetId}/block")
    public ResponseEntity<ApiResponse<Void>> unblock(@PathVariable String targetId) {
        friendshipService.unblock(CurrentUserContext.getUserId(), targetId);
        return ResponseEntity.ok(ApiResponse.success("User unblocked", null));
    }

    @GetMapping("/me/blocked")
    public ResponseEntity<ApiResponse<PageResponse<UserProfileResponse>>> listBlocked(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                friendshipService.listBlocked(CurrentUserContext.getUserId(), pageable)));
    }
}
