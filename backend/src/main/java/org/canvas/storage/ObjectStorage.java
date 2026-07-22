package org.canvas.storage;

import java.io.InputStream;
import java.util.Optional;

public interface ObjectStorage {
    StoredObject put(InputStream content, long byteSize, String mediaType);

    default StoredObject putGenerated(String objectKey, InputStream content, long byteSize, String mediaType) {
        return put(content, byteSize, mediaType);
    }

    void delete(String objectKey);

    default InputStream get(String objectKey) {
        throw new UnsupportedOperationException("Reading stored objects is not supported.");
    }

    default Optional<ObjectMetadata> head(String objectKey) {
        throw new UnsupportedOperationException("Inspecting stored objects is not supported.");
    }

    record StoredObject(String objectKey) {
    }

    record ObjectMetadata(long byteSize, String mediaType) {
    }
}
