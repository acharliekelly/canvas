package me.acharliekelly.canvas.storage;

import java.io.InputStream;
import java.util.UUID;
import java.util.Optional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

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
        if (!objectKey.matches("generated/(audio/[0-9a-f]{64}\\.wav|qr/[0-9a-f]{64}\\.png)")) {
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

    @Override
    public Optional<ObjectMetadata> head(String objectKey) {
        try {
            var response = client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(objectKey).build());
            return Optional.of(new ObjectMetadata(response.contentLength(), response.contentType()));
        } catch (S3Exception error) {
            if (error.statusCode() == 404) {
                return Optional.empty();
            }
            throw error;
        }
    }
}
