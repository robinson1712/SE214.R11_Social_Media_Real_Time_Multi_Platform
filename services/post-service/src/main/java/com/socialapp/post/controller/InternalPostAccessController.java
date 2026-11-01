package com.socialapp.post.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.ContentAccessResponse;
import com.socialapp.post.entity.Post;
import com.socialapp.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Internal API; no /api route is configured for it at the public gateway. */
@RestController
@RequestMapping("/internal/posts")
@RequiredArgsConstructor
public class InternalPostAccessController {

    private final PostService postService;

    @GetMapping("/{id}/comment-access")
    public ContentAccessResponse commentAccess(@PathVariable String id, @RequestParam String viewerId) {
        return postService.commentAccess(id, viewerId);
    }

    @GetMapping("/batch")
    public ApiResponse<List<Post>> batch(@RequestParam String ids, @RequestParam String viewerId) {
        List<String> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
        return ApiResponse.success(postService.getVisiblePostsByIds(idList, viewerId));
    }
}
