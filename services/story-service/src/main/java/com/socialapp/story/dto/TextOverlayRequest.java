package com.socialapp.story.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TextOverlayRequest(
        @NotBlank String text,
        String fontFamily,
        String color,
        @NotNull Double x,
        @NotNull Double y,
        Double fontSize
) {
}
