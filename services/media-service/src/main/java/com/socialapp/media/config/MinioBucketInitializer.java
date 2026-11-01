package com.socialapp.media.config;

import com.socialapp.media.storage.ObjectStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes a local MinIO bucket when requested. Managed storage profiles
 * disable initialization because bucket policy is controlled by the provider.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MinioBucketInitializer implements ApplicationRunner {

    private final ObjectStorageClient objectStorageClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.initialize-bucket:true}")
    private boolean initializeBucket;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!initializeBucket) {
            log.info("Skipping object storage bucket initialization for bucket '{}'", bucket);
            return;
        }

        objectStorageClient.initializePublicBucket(bucket);
        log.info("Initialized public object storage bucket '{}'", bucket);
    }
}
