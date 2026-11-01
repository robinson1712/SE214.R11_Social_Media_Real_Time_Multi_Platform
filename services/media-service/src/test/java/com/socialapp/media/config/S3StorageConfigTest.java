package com.socialapp.media.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class S3StorageConfigTest {

    @Test
    void s3Client_acceptsSupabaseEndpointWithPath() {
        URI endpoint = URI.create("https://project-ref.storage.supabase.co/storage/v1/s3");

        try (S3Client client = new S3StorageConfig().s3Client(
                endpoint.toString(), "ap-southeast-1", "access-key", "secret-key")) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).contains(endpoint);
            assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("ap-southeast-1");
        }
    }
}
