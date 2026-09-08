package com.socialapp.moderation.dto;

import com.socialapp.moderation.entity.ReportReason;
import com.socialapp.moderation.entity.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReportRequest(
        @NotNull ReportTargetType targetType,
        @NotBlank String targetId,
        @NotNull ReportReason reason,
        String description
) {
}
