package org.canvas.storage;

import java.io.InputStream;

public interface ObjectStorage {
    StoredObject put(InputStream content, long byteSize, String mediaType);

    void delete(String objectKey);

    default InputStream get(String objectKey) {
        throw new UnsupportedOperationException("Reading stored objects is not supported.");
    }

    record StoredObject(String objectKey) {
    }
}
