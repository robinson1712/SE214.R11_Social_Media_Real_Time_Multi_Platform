package com.socialapp.comment.controller;

import com.socialapp.comment.service.CommentService;
import com.socialapp.common.dto.ContentAccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Internal API used by reaction-service; it is not routed through the public gateway. */
@RestController
@RequestMapping("/internal/comments")
@RequiredArgsConstructor
public class InternalCommentAccessController {

    private final CommentService commentService;

    @GetMapping("/{id}/reaction-access")
    public ContentAccessResponse reactionAccess(@PathVariable String id, @RequestParam String viewerId) {
        return commentService.reactionAccess(id, viewerId);
    }
}
