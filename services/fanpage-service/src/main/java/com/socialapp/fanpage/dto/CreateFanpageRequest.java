package com.socialapp.fanpage.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateFanpageRequest(
        @NotBlank String name,
        String category,
        String description
) {
}
