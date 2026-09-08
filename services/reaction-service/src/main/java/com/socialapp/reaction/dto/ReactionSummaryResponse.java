package com.socialapp.reaction.dto;

import com.socialapp.common.enums.ReactionType;

import java.util.Map;

public record ReactionSummaryResponse(Map<ReactionType, Long> counts, long total) {
}
