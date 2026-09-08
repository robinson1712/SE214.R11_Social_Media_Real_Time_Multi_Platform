package com.socialapp.user.dto;

import jakarta.validation.constraints.NotBlank;

public record MediaUrlRequest(
        @NotBlank String mediaUrl
) {
}
