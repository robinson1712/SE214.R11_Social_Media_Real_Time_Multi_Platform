package com.socialapp.comment.controller;

import com.socialapp.comment.dto.CreateCommentRequest;
import com.socialapp.comment.dto.UpdateCommentRequest;
import com.socialapp.comment.entity.Comment;
import com.socialapp.comment.service.CommentService;
import com.socialapp.common.dto.ApiResponse;
import com.socialapp.common.dto.PageResponse;
import com.socialapp.common.enums.TargetType;
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

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<ApiResponse<Comment>> createComment(@RequestBody CreateCommentRequest request) {
        Comment comment = commentService.createComment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Comment created", comment));
    }

    // Was /post/{postId} — reels need comments too now, so the target is a
    // (targetType, targetId) pair instead of an implicit "always a post" path.
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<Comment>>> getTopLevelComments(
            @RequestParam TargetType targetType,
            @RequestParam String targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.from(commentService.getTopLevelComments(targetType, targetId, pageable))));
    }

    @GetMapping("/{id}/replies")
    public ResponseEntity<ApiResponse<PageResponse<Comment>>> getReplies(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(commentService.getReplies(id, pageable))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Comment>> updateComment(@PathVariable String id, @RequestBody UpdateCommentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Comment updated", commentService.updateComment(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable String id) {
        commentService.deleteComment(id);
        return ResponseEntity.ok(ApiResponse.success("Comment deleted", null));
    }
}
