package com.socialapp.dating.dto;

import com.socialapp.dating.entity.SwipeAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SwipeRequest(
        @NotBlank String targetId,
        @NotNull SwipeAction action
) {
}
