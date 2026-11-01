package com.socialapp.post.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "group-service")
public interface GroupAuthorizationClient {

    @GetMapping("/internal/groups/{groupId}/members/{userId}/approved")
    boolean isApprovedMember(@PathVariable("groupId") String groupId, @PathVariable("userId") String userId);

    @GetMapping("/internal/groups/{groupId}/can-view")
    boolean canViewContent(@PathVariable("groupId") String groupId, @RequestParam("viewerId") String viewerId);
}
