package com.socialapp.story.dto;

import com.socialapp.story.document.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateStoryRequest(
        @NotBlank String mediaUrl,
        @NotNull MediaType mediaType,
        String caption,
        List<TextOverlayRequest> textOverlays
) {
}
