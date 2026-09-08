package com.socialapp.post.controller;

import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.post.dto.CreatePostRequest;
import com.socialapp.post.dto.ShareRequest;
import com.socialapp.post.dto.UpdatePostRequest;
import com.socialapp.post.entity.Post;
import com.socialapp.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<ApiResponse<Post>> createPost(@RequestBody CreatePostRequest request) {
        Post post = postService.createPost(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Post created", post));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Post>> getPost(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(postService.getPost(id)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<Post>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(postService.searchPublicPosts(q, pageable))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Post>> updatePost(@PathVariable String id, @RequestBody UpdatePostRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Post updated", postService.updatePost(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable String id) {
        postService.deletePost(id);
        return ResponseEntity.ok(ApiResponse.success("Post deleted", null));
    }

    @GetMapping("/author/{authorId}")
    public ResponseEntity<ApiResponse<PageResponse<Post>>> getByAuthor(
            @PathVariable String authorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt")));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(postService.getPostsByAuthor(authorId, pageable))));
    }

    @PutMapping("/{id}/pin")
    public ResponseEntity<ApiResponse<Post>> pinPost(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Post pinned", postService.pinPost(id)));
    }

    @DeleteMapping("/{id}/pin")
    public ResponseEntity<ApiResponse<Post>> unpinPost(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Post unpinned", postService.unpinPost(id)));
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<ApiResponse<Post>> sharePost(@PathVariable String id, @RequestBody ShareRequest request) {
        Post shared = postService.sharePost(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Post shared", shared));
    }

    @PostMapping("/share-reel/{reelId}")
    public ResponseEntity<ApiResponse<Post>> shareReel(@PathVariable String reelId, @RequestBody ShareRequest request) {
        Post shared = postService.shareReel(reelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Reel shared", shared));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<PageResponse<Post>>> getByGroup(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(postService.getPostsByGroup(groupId, pageable))));
    }

    @GetMapping("/page/{pageId}")
    public ResponseEntity<ApiResponse<PageResponse<Post>>> getByPage(
            @PathVariable String pageId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(postService.getPostsByPage(pageId, pageable))));
    }

    @GetMapping("/batch")
    public ResponseEntity<ApiResponse<List<Post>>> getBatch(@RequestParam String ids) {
        List<String> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return ResponseEntity.ok(ApiResponse.success(postService.getPostsByIds(idList)));
    }
}
