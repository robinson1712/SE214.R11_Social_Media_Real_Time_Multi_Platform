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
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MediaService — MinioClient and the repository are mocked, so
 * these exercise only the service's own decisions (auth/ownership checks,
 * object-key construction, and how storage failures are translated).
 */
@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    private static final String BUCKET = "social-media";
    private static final String PUBLIC_ENDPOINT = "http://localhost:9000";

    @Mock
    private MinioClient minioClient;
    @Mock
    private MediaFileRepository mediaFileRepository;

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(minioClient, mediaFileRepository);
        ReflectionTestUtils.setField(mediaService, "publicEndpoint", PUBLIC_ENDPOINT);
        ReflectionTestUtils.setField(mediaService, "bucket", BUCKET);
    }

    private MediaFile mediaFile(String id, String ownerId, String objectKey) {
        return MediaFile.builder()
                .id(id)
                .ownerId(ownerId)
                .objectKey(objectKey)
                .url(PUBLIC_ENDPOINT + "/" + BUCKET + "/" + objectKey)
                .contentType("image/png")
                .purpose(MediaPurpose.AVATAR)
                .sizeBytes(4L)
                .createdAt(Instant.now())
                .build();
    }

    // ---- upload ----

    @Test
    void upload_authenticatedWithFile_putsObjectAndSavesRow() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "hello".getBytes());
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

        UploadResponse response = mediaService.upload("user-1", file, "avatar");

        ArgumentCaptor<PutObjectArgs> putCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putCaptor.capture());
        assertThat(putCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(putCaptor.getValue().object()).startsWith("AVATAR/user-1/");
        assertThat(putCaptor.getValue().object()).endsWith("-photo.png");

        ArgumentCaptor<MediaFile> saveCaptor = ArgumentCaptor.forClass(MediaFile.class);
        verify(mediaFileRepository).save(saveCaptor.capture());
        assertThat(saveCaptor.getValue().getOwnerId()).isEqualTo("user-1");
        assertThat(saveCaptor.getValue().getPurpose()).isEqualTo(MediaPurpose.AVATAR);
        assertThat(saveCaptor.getValue().getObjectKey()).isEqualTo(putCaptor.getValue().object());
        assertThat(saveCaptor.getValue().getUrl()).isEqualTo(PUBLIC_ENDPOINT + "/" + BUCKET + "/" + putCaptor.getValue().object());

        assertThat(response.url()).isEqualTo(saveCaptor.getValue().getUrl());
    }

    @Test
    void upload_purposeIsCaseInsensitiveAndTrimmed() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "pic.jpg", "image/jpeg", "data".getBytes());
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

        mediaService.upload("user-1", file, "  post ");

        ArgumentCaptor<MediaFile> saveCaptor = ArgumentCaptor.forClass(MediaFile.class);
        verify(mediaFileRepository).save(saveCaptor.capture());
        assertThat(saveCaptor.getValue().getPurpose()).isEqualTo(MediaPurpose.POST);
    }

    @Test
    void upload_sanitizesUnsafeCharactersInFilename() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "my photo!@#.png", "image/png", "hello".getBytes());
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

        mediaService.upload("user-1", file, "post");

        ArgumentCaptor<PutObjectArgs> putCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putCaptor.capture());
        assertThat(putCaptor.getValue().object()).doesNotContain(" ", "!", "@", "#");
        assertThat(putCaptor.getValue().object()).endsWith("-my_photo___.png");
    }

    @Test
    void upload_notAuthenticated_throwsUnauthorizedAndNeverTouchesStorageOrDb() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "hello".getBytes());

        assertThatThrownBy(() -> mediaService.upload(null, file, "avatar"))
                .isInstanceOf(UnauthorizedException.class);

        verify(minioClient, never()).putObject(any());
        verify(mediaFileRepository, never()).save(any());
    }

    @Test
    void upload_nullFile_throwsBadRequest() {
        assertThatThrownBy(() -> mediaService.upload("user-1", null, "avatar"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void upload_emptyFile_throwsBadRequest() {
        MultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> mediaService.upload("user-1", file, "avatar"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void upload_missingPurpose_throwsBadRequest() {
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "hello".getBytes());

        assertThatThrownBy(() -> mediaService.upload("user-1", file, null))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> mediaService.upload("user-1", file, "  "))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void upload_unsupportedContentType_throwsBadRequestAndNeverTouchesStorage() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "script.exe", "application/x-msdownload", "hello".getBytes());

        assertThatThrownBy(() -> mediaService.upload("user-1", file, "post"))
                .isInstanceOf(BadRequestException.class);

        verify(minioClient, never()).putObject(any());
        verify(mediaFileRepository, never()).save(any());
    }

    @Test
    void upload_videoForImageOnlyPurpose_throwsBadRequest() {
        MultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", "hello".getBytes());

        assertThatThrownBy(() -> mediaService.upload("user-1", file, "avatar"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void upload_videoForVideoCapablePurpose_isAllowed() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", "hello".getBytes());
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

        mediaService.upload("user-1", file, "reel");

        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void upload_oversizedImageForImageOnlyPurpose_throwsBadRequest() {
        byte[] tooLarge = new byte[11 * 1024 * 1024];
        MultipartFile file = new MockMultipartFile("file", "huge.png", "image/png", tooLarge);

        assertThatThrownBy(() -> mediaService.upload("user-1", file, "cover"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void upload_invalidPurpose_throwsBadRequest() {
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "hello".getBytes());

        assertThatThrownBy(() -> mediaService.upload("user-1", file, "not-a-real-purpose"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void upload_minioPutObjectThrows_wrapsAsBadRequestAndNeverSaves() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "hello".getBytes());
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(mock(ErrorResponseException.class));

        assertThatThrownBy(() -> mediaService.upload("user-1", file, "avatar"))
                .isInstanceOf(BadRequestException.class);

        verify(mediaFileRepository, never()).save(any());
    }

    // ---- delete ----

    @Test
    void delete_byOwner_removesFromStorageAndDeletesRow() throws Exception {
        MediaFile file = mediaFile("media-1", "user-1", "AVATAR/user-1/abc-photo.png");
        when(mediaFileRepository.findById("media-1")).thenReturn(Optional.of(file));

        mediaService.delete("user-1", "media-1");

        ArgumentCaptor<RemoveObjectArgs> removeCaptor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(removeCaptor.capture());
        assertThat(removeCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(removeCaptor.getValue().object()).isEqualTo("AVATAR/user-1/abc-photo.png");

        verify(mediaFileRepository).delete(file);
    }

    @Test
    void delete_byNonOwner_throwsForbiddenAndNeverTouchesStorageOrDb() throws Exception {
        MediaFile file = mediaFile("media-1", "user-1", "AVATAR/user-1/abc-photo.png");
        when(mediaFileRepository.findById("media-1")).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> mediaService.delete("user-2", "media-1"))
                .isInstanceOf(ForbiddenException.class);

        verify(minioClient, never()).removeObject(any());
        verify(mediaFileRepository, never()).delete(any());
    }

    @Test
    void delete_notFound_throwsResourceNotFound() {
        when(mediaFileRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.delete("user-1", "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_notAuthenticated_throwsUnauthorized() {
        assertThatThrownBy(() -> mediaService.delete(null, "media-1"))
                .isInstanceOf(UnauthorizedException.class);

        verify(mediaFileRepository, never()).findById(any());
    }

    @Test
    void delete_minioRemoveObjectThrows_wrapsAsBadRequestAndNeverDeletesRow() throws Exception {
        MediaFile file = mediaFile("media-1", "user-1", "AVATAR/user-1/abc-photo.png");
        when(mediaFileRepository.findById("media-1")).thenReturn(Optional.of(file));
        org.mockito.Mockito.doThrow(mock(ErrorResponseException.class)).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> mediaService.delete("user-1", "media-1"))
                .isInstanceOf(BadRequestException.class);

        verify(mediaFileRepository, never()).delete(any());
    }

    // ---- get ----

    @Test
    void get_found_returnsResponse() {
        MediaFile file = mediaFile("media-1", "user-1", "AVATAR/user-1/abc-photo.png");
        when(mediaFileRepository.findById("media-1")).thenReturn(Optional.of(file));

        MediaFileResponse response = mediaService.get("media-1");

        assertThat(response.id()).isEqualTo("media-1");
        assertThat(response.ownerId()).isEqualTo("user-1");
        assertThat(response.purpose()).isEqualTo(MediaPurpose.AVATAR);
    }

    @Test
    void get_notFound_throwsResourceNotFound() {
        when(mediaFileRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.get("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
