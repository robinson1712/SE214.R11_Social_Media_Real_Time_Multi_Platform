package com.socialapp.feed.client;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.feed.dto.GroupMemberDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "group-service", fallbackFactory = GroupClientFallbackFactory.class)
public interface GroupClient {

    @GetMapping("/api/groups/{id}/members")
    ApiResponse<PageResponse<GroupMemberDto>> getMembers(@PathVariable("id") String id,
                                                           @RequestParam("size") int size);
}
