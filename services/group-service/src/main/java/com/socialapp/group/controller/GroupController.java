package com.socialapp.group.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.group.dto.ChangeRoleRequest;
import com.socialapp.group.dto.CreateGroupRequest;
import com.socialapp.group.entity.Group;
import com.socialapp.group.entity.GroupMember;
import com.socialapp.group.service.GroupService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<ApiResponse<Group>> create(@Valid @RequestBody CreateGroupRequest request) {
        Group group = groupService.createGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Group created", group));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Group>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(groupService.getGroup(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<Group>>> list(
            @RequestParam(required = false) String name,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(groupService.listVisibleGroups(name, pageable))));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<GroupMember>> join(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Join processed", groupService.join(id)));
    }

    @PutMapping("/{id}/members/{userId}/approve")
    public ResponseEntity<ApiResponse<GroupMember>> approve(@PathVariable String id, @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success("Member approved", groupService.approveMember(id, userId)));
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<ApiResponse<Void>> leave(@PathVariable String id) {
        groupService.leave(id);
        return ResponseEntity.ok(ApiResponse.success("Left group", null));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable String id, @PathVariable String userId) {
        groupService.removeMember(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Member removed", null));
    }

    @PutMapping("/{id}/members/{userId}/role")
    public ResponseEntity<ApiResponse<GroupMember>> changeRole(
            @PathVariable String id, @PathVariable String userId, @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Role updated", groupService.changeRole(id, userId, request.role())));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<PageResponse<GroupMember>>> members(
            @PathVariable String id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(groupService.listMembers(id, pageable))));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<Group>>> myGroups(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(groupService.myGroups(pageable))));
    }
}
