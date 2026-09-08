package com.socialapp.search.client;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.search.dto.UserSearchResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service", fallbackFactory = UserServiceClientFallbackFactory.class)
public interface UserServiceClient {

    @GetMapping("/api/users/search")
    ApiResponse<PageResponse<UserSearchResult>> search(
            @RequestParam("q") String q,
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}
