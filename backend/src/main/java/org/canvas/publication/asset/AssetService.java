package org.canvas.publication.asset;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.canvas.publication.asset.AudioGenerator.ApprovedDescriptionInput;
import org.canvas.publication.asset.AudioGenerator.GeneratedBinary;
import org.canvas.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    AssetService(GeneratedAssetRepository repository, AudioGenerator audioGenerator,
            QrCodeGenerator qrCodeGenerator, ObjectStorage storage) {
        this.repository = repository;
        this.audioGenerator = audioGenerator;
        this.qrCodeGenerator = qrCodeGenerator;
        this.storage = storage;
    }

    @Transactional
    public GeneratedAsset audioFor(ApprovedDescriptionInput input) {
        String inputKey = sha256(audioGenerator.cacheNamespace() + "\n" + input.revisionId()
                + "\n" + input.label() + "\n" + input.text());
        return repository.findByKindAndInputKey(AssetKind.AUDIO, inputKey)
                .orElseGet(() -> storeAudio(input, inputKey));
    }

    @Transactional
    public GeneratedAsset qrFor(URI publicUri, UUID sourcePublicationId) {
        String inputKey = sha256(qrCodeGenerator.cacheNamespace() + "\n" + publicUri.toASCIIString());
        return repository.findByKindAndInputKey(AssetKind.QR_CODE, inputKey)
                .orElseGet(() -> storeQr(publicUri, sourcePublicationId, inputKey));
    }

    @Transactional(readOnly = true)
    public GeneratedAsset existingAudio(ApprovedDescriptionInput input) {
        return repository.findByKindAndInputKey(AssetKind.AUDIO,
                        sha256(audioGenerator.cacheNamespace() + "\n" + input.revisionId()
                                + "\n" + input.label() + "\n" + input.text()))
                .orElseThrow(() -> new AssetProblem("Published audio is unavailable."));
    }

    @Transactional(readOnly = true)
    public GeneratedAsset existingQr(URI publicUri) {
        return repository.findByKindAndInputKey(AssetKind.QR_CODE,
                        sha256(qrCodeGenerator.cacheNamespace() + "\n" + publicUri.toASCIIString()))
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

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform.", impossible);
        }
    }

    public static class AssetProblem extends RuntimeException {
        public AssetProblem(String message) { super(message); }
        public AssetProblem(String message, Throwable cause) { super(message, cause); }
    }
}
