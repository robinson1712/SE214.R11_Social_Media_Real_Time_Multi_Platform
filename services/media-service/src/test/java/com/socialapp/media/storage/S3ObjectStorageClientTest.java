package com.socialapp.media.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageClientTest {

    @Mock
    private S3Client s3Client;

    private S3ObjectStorageClient storageClient;

    @BeforeEach
    void setUp() {
        storageClient = new S3ObjectStorageClient(s3Client);
    }

    @Test
    void putObject_preservesBucketKeySizeAndContentType() {
        byte[] content = "hello".getBytes();

        storageClient.putObject("social-media", "POST/user-1/file.png",
                new ByteArrayInputStream(content), content.length, "image/png");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("social-media");
        assertThat(request.getValue().key()).isEqualTo("POST/user-1/file.png");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void removeObject_preservesBucketAndKey() {
        storageClient.removeObject("social-media", "POST/user-1/file.png");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("social-media");
        assertThat(request.getValue().key()).isEqualTo("POST/user-1/file.png");
    }

    @Test
    void initializePublicBucket_onlyChecksManagedBucketExists() {
        storageClient.initializePublicBucket("social-media");

        ArgumentCaptor<HeadBucketRequest> request = ArgumentCaptor.forClass(HeadBucketRequest.class);
        verify(s3Client).headBucket(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("social-media");
    }
}
