package com.socialapp.comment.dto;

import com.socialapp.common.enums.TargetType;

public record CreateCommentRequest(
        TargetType targetType, String targetId, String targetOwnerId, String content, String parentCommentId) {
}
