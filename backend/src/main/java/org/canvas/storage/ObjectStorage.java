package org.canvas.storage;

import java.io.InputStream;

public interface ObjectStorage {
    StoredObject put(InputStream content, long byteSize, String mediaType);

    void delete(String objectKey);

    record StoredObject(String objectKey) {
    }
}
