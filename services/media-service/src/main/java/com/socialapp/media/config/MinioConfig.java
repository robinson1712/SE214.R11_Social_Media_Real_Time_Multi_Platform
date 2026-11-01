package com.socialapp.media.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "object-storage", name = "provider", havingValue = "minio", matchIfMissing = true)
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${minio.internal-endpoint}") String internalEndpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(internalEndpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
