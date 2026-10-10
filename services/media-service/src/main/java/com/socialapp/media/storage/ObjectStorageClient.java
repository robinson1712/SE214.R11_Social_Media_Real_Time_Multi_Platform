package com.socialapp.media.storage;

import java.io.InputStream;

public interface ObjectStorageClient {

    void putObject(String bucket, String objectKey, InputStream inputStream, long size, String contentType)
            throws Exception;

    void removeObject(String bucket, String objectKey) throws Exception;

    void initializePublicBucket(String bucket) throws Exception;
}
