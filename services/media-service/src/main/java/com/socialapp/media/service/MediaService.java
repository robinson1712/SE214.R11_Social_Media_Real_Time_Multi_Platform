package com.socialapp.media.service;

import com.socialapp.common.exception.BadRequestException;
import com.socialapp.common.exception.ForbiddenException;
import com.socialapp.common.exception.ResourceNotFoundException;
import com.socialapp.common.exception.UnauthorizedException;
import com.socialapp.media.dto.MediaFileResponse;
import com.socialapp.media.dto.UploadResponse;
import com.socialapp.media.entity.MediaFile;
import com.socialapp.media.entity.MediaPurpose;
import com.socialapp.media.repository.MediaFileRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    // Content-type whitelist — the servlet-level max-file-size config only bounds
    // size, not type, so without this any file (executables, scripts, ...) could
    // be uploaded and served back from MinIO's public URL.
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Set<String> VIDEO_CONTENT_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime");
    // Voice messages (chat only) — browsers' MediaRecorder API produces
    // audio/webm almost universally; the rest are kept for headroom (e.g. a
    // future mobile client recording to a different container).
    private static final Set<String> AUDIO_CONTENT_TYPES = Set.of(
            "audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav", "audio/x-wav");
    // File attachments (chat only) — a fixed whitelist of common office/
    // document formats, same reasoning as the image/video whitelist above:
    // without one, chat could be used to upload and redistribute arbitrary
    // files (executables, scripts) from MinIO's public URL.
    private static final Set<String> DOCUMENT_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/zip",
            "application/x-zip-compressed",
            "text/plain");
    private static final long IMAGE_MAX_BYTES = 10L * 1024 * 1024;
    // Profile/page/group art is always a still image and small — everything else
    // (post/story/reel/chat) may legitimately be video, bounded only by the
    // existing servlet-level max-file-size (50MB).
    private static final Set<MediaPurpose> IMAGE_ONLY_PURPOSES =
            EnumSet.of(MediaPurpose.AVATAR, MediaPurpose.COVER, MediaPurpose.PAGE, MediaPurpose.GROUP);
    // Voice clips and file attachments only make sense as chat messages —
    // posts/stories/reels stay image-or-video, same as before this change.
    private static final Set<MediaPurpose> ATTACHMENT_PURPOSES = EnumSet.of(MediaPurpose.CHAT);

    private final MinioClient minioClient;
    private final MediaFileRepository mediaFileRepository;

    @Value("${minio.public-endpoint}")
    private String publicEndpoint;

    @Value("${minio.bucket}")
    private String bucket;

    @Transactional
    public UploadResponse upload(String currentUserId, MultipartFile file, String purposeRaw) {
        if (currentUserId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        MediaPurpose purpose = parsePurpose(purposeRaw);
        validateFile(file, purpose);

        String originalFilename = sanitize(file.getOriginalFilename());
        String objectKey = "%s/%s/%s-%s".formatted(purpose.name(), currentUserId, UUID.randomUUID(), originalFilename);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            throw new BadRequestException("Failed to upload file: " + e.getMessage());
        }

        String url = publicEndpoint + "/" + bucket + "/" + objectKey;

        MediaFile mediaFile = MediaFile.builder()
                .ownerId(currentUserId)
                .objectKey(objectKey)
                .url(url)
                .contentType(file.getContentType())
                .purpose(purpose)
                .sizeBytes(file.getSize())
                .createdAt(Instant.now())
                .build();
        mediaFile = mediaFileRepository.save(mediaFile);

        return new UploadResponse(mediaFile.getId(), mediaFile.getUrl());
    }

    @Transactional
    public void delete(String currentUserId, String id) {
        if (currentUserId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        MediaFile mediaFile = findOrThrow(id);
        if (!mediaFile.getOwnerId().equals(currentUserId)) {
            throw new ForbiddenException("Only the owner may delete this media file");
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(mediaFile.getObjectKey())
                    .build());
        } catch (Exception e) {
            throw new BadRequestException("Failed to delete file from storage: " + e.getMessage());
        }
        mediaFileRepository.delete(mediaFile);
    }

    public MediaFileResponse get(String id) {
        return MediaFileResponse.from(findOrThrow(id));
    }

    private MediaFile findOrThrow(String id) {
        return mediaFileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media file not found: " + id));
    }

    private MediaPurpose parsePurpose(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("purpose is required");
        }
        try {
            return MediaPurpose.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid purpose: " + raw);
        }
    }

    private void validateFile(MultipartFile file, MediaPurpose purpose) {
        String contentType = file.getContentType();
        boolean isImage = contentType != null && IMAGE_CONTENT_TYPES.contains(contentType);
        boolean isVideo = contentType != null && VIDEO_CONTENT_TYPES.contains(contentType);
        boolean isAttachment = ATTACHMENT_PURPOSES.contains(purpose) && contentType != null
                && (AUDIO_CONTENT_TYPES.contains(contentType) || DOCUMENT_CONTENT_TYPES.contains(contentType));
        if (!isImage && !isVideo && !isAttachment) {
            throw new BadRequestException("Unsupported file type: " + contentType);
        }
        if (IMAGE_ONLY_PURPOSES.contains(purpose)) {
            if (isVideo) {
                throw new BadRequestException(purpose + " must be an image, not a video");
            }
            if (file.getSize() > IMAGE_MAX_BYTES) {
                throw new BadRequestException(purpose + " image must be 10MB or smaller");
            }
        }
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
