package com.socialapp.group.controller;

import com.socialapp.group.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Private service-to-service authorization API. It intentionally lives outside
 * the public /api/groups route so the API gateway does not expose it.
 */
@RestController
@RequestMapping("/internal/groups")
@RequiredArgsConstructor
public class InternalGroupAuthorizationController {

    private final GroupService groupService;

    @GetMapping("/{groupId}/members/{userId}/approved")
    public boolean isApprovedMember(@PathVariable String groupId, @PathVariable String userId) {
        return groupService.canCreatePost(groupId, userId);
    }

    @GetMapping("/{groupId}/can-view")
    public boolean canViewContent(@PathVariable String groupId, @RequestParam(required = false) String viewerId) {
        return groupService.canViewContent(groupId, viewerId);
    }
}
