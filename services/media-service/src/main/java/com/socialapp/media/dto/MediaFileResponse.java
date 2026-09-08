package com.socialapp.media.dto;

import com.socialapp.media.entity.MediaFile;
import com.socialapp.media.entity.MediaPurpose;

import java.time.Instant;

public record MediaFileResponse(
        String id,
        String ownerId,
        String url,
        String contentType,
        MediaPurpose purpose,
        long sizeBytes,
        Instant createdAt
) {
    public static MediaFileResponse from(MediaFile m) {
        return new MediaFileResponse(m.getId(), m.getOwnerId(), m.getUrl(), m.getContentType(),
                m.getPurpose(), m.getSizeBytes(), m.getCreatedAt());
    }
}
