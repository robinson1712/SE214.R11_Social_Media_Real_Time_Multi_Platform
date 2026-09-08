package com.socialapp.group.dto;

import com.socialapp.group.entity.GroupPrivacy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateGroupRequest(
        @NotBlank String name,
        String description,
        @NotNull GroupPrivacy privacy
) {
}
