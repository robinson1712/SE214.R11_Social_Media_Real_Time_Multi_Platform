package com.socialapp.moderation.dto;

import jakarta.validation.constraints.NotNull;

public record ResolveReportRequest(
        @NotNull ModerationAction action,
        String note
) {
    public enum ModerationAction {
        DISMISS,
        REMOVE_CONTENT
    }
}
