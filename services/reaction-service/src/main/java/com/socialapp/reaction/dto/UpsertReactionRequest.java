package com.socialapp.reaction.dto;

import com.socialapp.common.enums.ReactionType;
import com.socialapp.common.enums.TargetType;

public record UpsertReactionRequest(TargetType targetType, String targetId, String targetOwnerId, ReactionType type) {
}
