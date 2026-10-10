package com.socialapp.comment.service;

import com.socialapp.comment.client.PostAccessClient;
import com.socialapp.comment.client.ReelAccessClient;
import com.socialapp.comment.client.StoryAccessClient;
import com.socialapp.common.dto.ContentAccessResponse;
import com.socialapp.common.enums.TargetType;
import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommentTargetAccessService {

    private final PostAccessClient postAccessClient;
    private final ReelAccessClient reelAccessClient;
    private final StoryAccessClient storyAccessClient;

    public ContentAccessResponse requireReadable(TargetType type, String targetId, String viewerId) {
        ContentAccessResponse result = check(type, targetId, viewerId);
        if (!result.exists()) {
            throw new ResourceNotFoundException("Comment target not found");
        }
        if (!result.accessible()) {
            throw new ForbiddenException("You do not have permission to view or comment on this content");
        }
        return result;
    }

    public ContentAccessResponse check(TargetType type, String targetId, String viewerId) {
        ContentAccessResponse result = switch (type) {
            case POST -> postAccessClient.check(targetId, normalizedViewer(viewerId));
            case REEL -> reelAccessClient.check(targetId, normalizedViewer(viewerId));
            case STORY -> storyAccessClient.check(targetId, normalizedViewer(viewerId));
            case COMMENT -> throw new BadRequestException("Comments cannot be used as content targets");
        };
        return result;
    }

    private String normalizedViewer(String viewerId) {
        return viewerId == null ? "" : viewerId;
    }
}
