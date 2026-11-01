package com.socialapp.media.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "object-storage", name = "provider", havingValue = "s3")
public class S3ObjectStorageClient implements ObjectStorageClient {

    private final S3Client s3Client;

    @Override
    public void putObject(String bucket, String objectKey, InputStream inputStream, long size, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size));
    }

    @Override
    public void removeObject(String bucket, String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
    }

    @Override
    public void initializePublicBucket(String bucket) {
        // Managed buckets and public-read policy are created in the provider UI.
        s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
    }
}
