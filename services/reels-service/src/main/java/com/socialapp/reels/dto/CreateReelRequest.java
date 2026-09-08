package com.socialapp.reels.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateReelRequest(
        @NotBlank String videoUrl,
        String thumbnailUrl,
        String caption
) {
}
