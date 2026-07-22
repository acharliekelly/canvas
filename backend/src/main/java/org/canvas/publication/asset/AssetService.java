package org.canvas.publication.asset;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.canvas.publication.asset.AudioGenerator.ApprovedDescriptionInput;
import org.canvas.publication.asset.AudioGenerator.GeneratedBinary;
import org.canvas.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AssetService {
    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    private final GeneratedAssetRepository repository;
    private final AudioGenerator audioGenerator;
    private final QrCodeGenerator qrCodeGenerator;
    private final ObjectStorage storage;
    private final ConcurrentHashMap<AssetCacheKey, LockEntry> inputLocks = new ConcurrentHashMap<>();

    AssetService(GeneratedAssetRepository repository, AudioGenerator audioGenerator,
            QrCodeGenerator qrCodeGenerator, ObjectStorage storage) {
        this.repository = repository;
        this.audioGenerator = audioGenerator;
        this.qrCodeGenerator = qrCodeGenerator;
        this.storage = storage;
    }

    @Transactional
    public GeneratedAsset audioFor(ApprovedDescriptionInput input) {
        String inputKey = audioInputKey(input);
        serializeForTransaction(AssetKind.AUDIO, inputKey);
        return repository.findByKindAndInputKey(AssetKind.AUDIO, inputKey)
                .orElseGet(() -> storeAudio(input, inputKey));
    }

    @Transactional
    public GeneratedAsset qrFor(URI publicUri, UUID sourcePublicationId) {
        String inputKey = qrInputKey(publicUri);
        serializeForTransaction(AssetKind.QR_CODE, inputKey);
        return repository.findByKindAndInputKey(AssetKind.QR_CODE, inputKey)
                .orElseGet(() -> storeQr(publicUri, sourcePublicationId, inputKey));
    }

    @Transactional(readOnly = true)
    public GeneratedAsset existingAudio(ApprovedDescriptionInput input) {
        return repository.findByKindAndInputKey(AssetKind.AUDIO, audioInputKey(input))
                .orElseThrow(() -> new AssetProblem("Published audio is unavailable."));
    }

    @Transactional(readOnly = true)
    public GeneratedAsset existingQr(URI publicUri) {
        return repository.findByKindAndInputKey(AssetKind.QR_CODE, qrInputKey(publicUri))
                .orElseThrow(() -> new AssetProblem("Published QR code is unavailable."));
    }

    public InputStream content(GeneratedAsset asset) {
        try {
            return storage.get(asset.getObjectKey());
        } catch (RuntimeException error) {
            throw new AssetProblem("Published asset is temporarily unavailable.", error);
        }
    }

    private GeneratedAsset storeAudio(ApprovedDescriptionInput input, String inputKey) {
        GeneratedBinary binary = audioGenerator.generate(input);
        ObjectStorage.StoredObject stored = store("generated/audio/" + inputKey + ".wav", binary);
        return repository.saveAndFlush(GeneratedAsset.audio(
                inputKey, binary, stored.objectKey(), input.revisionId()));
    }

    private GeneratedAsset storeQr(URI publicUri, UUID sourcePublicationId, String inputKey) {
        GeneratedBinary binary = qrCodeGenerator.generate(publicUri);
        ObjectStorage.StoredObject stored = store("generated/qr/" + inputKey + ".png", binary);
        return repository.saveAndFlush(GeneratedAsset.qrCode(
                inputKey, binary, stored.objectKey(), sourcePublicationId));
    }

    private ObjectStorage.StoredObject store(String objectKey, GeneratedBinary binary) {
        byte[] bytes = binary.bytes();
        ObjectStorage.StoredObject stored = storage.putGenerated(objectKey,
                new ByteArrayInputStream(bytes), bytes.length, binary.mediaType());
        registerRollbackCompensation(stored.objectKey());
        return stored;
    }

    private void registerRollbackCompensation(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Generated asset storage requires an active transaction.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public int getOrder() {
                return Ordered.LOWEST_PRECEDENCE - 1;
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    storage.delete(objectKey);
                } catch (RuntimeException compensationFailure) {
                    log.error("Failed to delete generated object after transaction rollback; objectKey={}",
                            objectKey, compensationFailure);
                }
            }
        });
    }

    private void serializeForTransaction(AssetKind kind, String inputKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Generated asset caching requires an active transaction.");
        }
        AssetCacheKey cacheKey = new AssetCacheKey(kind, inputKey);
        LockEntry entry = inputLocks.compute(cacheKey, (ignored, current) -> {
            LockEntry next = current == null ? new LockEntry() : current;
            next.references++;
            return next;
        });
        entry.lock.lock();
        try {
            // The shared deterministic object remains owned by this transaction until commit or
            // rollback cleanup finishes, so a waiter can never overwrite it before compensation.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public int getOrder() {
                    return Ordered.LOWEST_PRECEDENCE;
                }

                @Override
                public void afterCompletion(int status) {
                    release(cacheKey, entry);
                }
            });
        } catch (RuntimeException | Error registrationFailure) {
            release(cacheKey, entry);
            throw registrationFailure;
        }
    }

    private void release(AssetCacheKey cacheKey, LockEntry entry) {
        entry.lock.unlock();
        inputLocks.compute(cacheKey, (ignored, current) -> {
            if (current != entry) {
                throw new IllegalStateException("Generated asset lock ownership changed unexpectedly.");
            }
            entry.references--;
            return entry.references == 0 ? null : entry;
        });
    }

    private String audioInputKey(ApprovedDescriptionInput input) {
        return canonicalSha256(List.of(
                new InputField("format", "canvas-generated-asset-key-v1"),
                new InputField("kind", "audio"),
                new InputField("generator", audioGenerator.cacheNamespace()),
                new InputField("revision-id", input.revisionId().toString()),
                new InputField("label", input.label()),
                new InputField("text", input.text())));
    }

    private String qrInputKey(URI publicUri) {
        return canonicalSha256(List.of(
                new InputField("format", "canvas-generated-asset-key-v1"),
                new InputField("kind", "qr-code"),
                new InputField("generator", qrCodeGenerator.cacheNamespace()),
                new InputField("public-uri", publicUri.toASCIIString())));
    }

    private static String canonicalSha256(List<InputField> fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (InputField field : fields) {
                updateLengthPrefixed(digest, field.tag());
                updateLengthPrefixed(digest, field.value());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform.", impossible);
        }
    }

    private static void updateLengthPrefixed(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private record InputField(String tag, String value) {}

    private record AssetCacheKey(AssetKind kind, String inputKey) {}

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }

    public static class AssetProblem extends RuntimeException {
        public AssetProblem(String message) { super(message); }
        public AssetProblem(String message, Throwable cause) { super(message, cause); }
    }
}
