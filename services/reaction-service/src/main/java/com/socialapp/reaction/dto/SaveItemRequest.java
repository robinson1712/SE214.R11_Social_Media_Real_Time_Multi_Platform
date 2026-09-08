package com.socialapp.reaction.dto;

import com.socialapp.common.enums.TargetType;

public record SaveItemRequest(TargetType targetType, String targetId, String targetOwnerId) {
}
