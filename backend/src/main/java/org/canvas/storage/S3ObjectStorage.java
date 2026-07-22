package org.canvas.storage;

import java.io.InputStream;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3ObjectStorage implements ObjectStorage {
    private final S3Client client;
    private final String bucket;

    public S3ObjectStorage(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public StoredObject put(InputStream content, long byteSize, String mediaType) {
        return putAt("artworks/" + UUID.randomUUID(), content, byteSize, mediaType);
    }

    @Override
    public StoredObject putGenerated(String objectKey, InputStream content, long byteSize, String mediaType) {
        if (!objectKey.matches("generated/(audio|qr)/[0-9a-f]{64}\\.(wav|png)")) {
            throw new IllegalArgumentException("Generated object key is not content-addressed.");
        }
        return putAt(objectKey, content, byteSize, mediaType);
    }

    private StoredObject putAt(String key, InputStream content, long byteSize, String mediaType) {
        client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(mediaType)
                        .contentLength(byteSize)
                        .build(),
                RequestBody.fromInputStream(content, byteSize));
        return new StoredObject(key);
    }

    @Override
    public void delete(String objectKey) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
    }

    @Override
    public InputStream get(String objectKey) {
        return client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
    }
}
