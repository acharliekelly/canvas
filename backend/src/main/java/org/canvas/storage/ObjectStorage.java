package org.canvas.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Port for one configured private S3-compatible bucket. Instances are qualified for either
 * artwork originals or generated assets, so callers must use the instance chosen for their asset
 * class rather than treating storage as a domain-workflow service.
 */
public interface ObjectStorage {
    /**
     * Stores an original at a storage-selected key.
     *
     * <p>This method consumes a caller-owned stream and does not promise to close it; the caller
     * must close the stream after this call returns.
     *
     * @param content caller-owned content stream
     * @return the exact persisted key, for later compensation or repair checks
     */
    StoredObject put(InputStream content, long byteSize, String mediaType);

    /**
     * Stores a generated asset at its caller-selected deterministic key.
     *
     * <p>This method consumes a caller-owned stream and does not promise to close it; the caller
     * must close the stream after this call returns.
     *
     * @param objectKey exact key used for the generated object
     * @param content caller-owned content stream
     * @return the exact persisted key, for later compensation or repair checks
     */
    default StoredObject putGenerated(String objectKey, InputStream content, long byteSize, String mediaType) {
        return put(content, byteSize, mediaType);
    }

    /**
     * Deletes an object, including when a prior rollback or repair already removed its key.
     * Callers may therefore use deletion as idempotent compensation.
     */
    void delete(String objectKey);

    /**
     * Opens the object content stream. The caller must close the returned stream.
     *
     * @return a caller-owned stream for the requested object
     */
    default InputStream get(String objectKey) {
        throw new UnsupportedOperationException("Reading stored objects is not supported.");
    }

    /**
     * Inspects an object for repair validation.
     *
     * @return empty only when the object is missing; other storage failures propagate to the caller
     */
    default Optional<ObjectMetadata> head(String objectKey) {
        throw new UnsupportedOperationException("Inspecting stored objects is not supported.");
    }

    /**
     * Exact persisted key returned by storage so callers can compensate for a failed workflow or
     * later verify a repair.
     */
    record StoredObject(String objectKey) {
    }

    /**
     * Exact persisted byte count and media type used to determine whether a stored object is safe
     * to reuse during compensation and repair checks.
     */
    record ObjectMetadata(long byteSize, String mediaType) {
    }
}
