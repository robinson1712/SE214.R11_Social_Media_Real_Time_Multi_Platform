package com.socialapp.fanpage.dto;

import com.socialapp.fanpage.entity.AdminRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddAdminRequest(
        @NotBlank String userId,
        @NotNull AdminRole role
) {
}
