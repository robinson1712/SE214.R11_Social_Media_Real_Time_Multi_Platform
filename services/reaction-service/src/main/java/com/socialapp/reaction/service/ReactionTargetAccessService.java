package com.socialapp.reaction.service;

import com.socialapp.common.dto.ContentAccessResponse;
import com.socialapp.common.enums.TargetType;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.reaction.client.CommentAccessClient;
import com.socialapp.reaction.client.PostAccessClient;
import com.socialapp.reaction.client.ReelAccessClient;
import com.socialapp.reaction.client.StoryAccessClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReactionTargetAccessService {

    private final PostAccessClient postAccessClient;
    private final ReelAccessClient reelAccessClient;
    private final StoryAccessClient storyAccessClient;
    private final CommentAccessClient commentAccessClient;

    public ContentAccessResponse requireReadable(TargetType type, String targetId, String viewerId) {
        ContentAccessResponse access = check(type, targetId, viewerId);
        if (!access.exists()) {
            throw new ResourceNotFoundException("Reaction target not found");
        }
        if (!access.accessible()) {
            throw new ForbiddenException("You do not have permission to interact with this content");
        }
        return access;
    }

    private ContentAccessResponse check(TargetType type, String targetId, String viewerId) {
        String normalizedViewer = viewerId == null ? "" : viewerId;
        return switch (type) {
            case POST -> postAccessClient.check(targetId, normalizedViewer);
            case REEL -> reelAccessClient.check(targetId, normalizedViewer);
            case STORY -> storyAccessClient.check(targetId, normalizedViewer);
            case COMMENT -> commentAccessClient.check(targetId, normalizedViewer);
        };
    }
}
