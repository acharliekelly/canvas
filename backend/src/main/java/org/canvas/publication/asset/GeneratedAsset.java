package org.canvas.publication.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "generated_assets", uniqueConstraints =
        @UniqueConstraint(name = "generated_assets_kind_input_unique", columnNames = {"kind", "input_key"}))
public class GeneratedAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetKind kind;

    @Column(name = "input_key", nullable = false, length = 64)
    private String inputKey;

    @Column(name = "media_type", nullable = false, length = 64)
    private String mediaType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "object_key", nullable = false, unique = true, length = 512)
    private String objectKey;

    @Column(nullable = false, length = 64)
    private String generator;

    @Column(name = "source_revision_id")
    private UUID sourceRevisionId;

    @Column(name = "source_publication_id")
    private UUID sourcePublicationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GeneratedAsset() {
    }

    static GeneratedAsset audio(String inputKey, AudioGenerator.GeneratedBinary binary,
            String objectKey, UUID sourceRevisionId) {
        return new GeneratedAsset(AssetKind.AUDIO, inputKey, binary, objectKey, sourceRevisionId, null);
    }

    static GeneratedAsset qrCode(String inputKey, AudioGenerator.GeneratedBinary binary,
            String objectKey, UUID sourcePublicationId) {
        return new GeneratedAsset(AssetKind.QR_CODE, inputKey, binary, objectKey, null, sourcePublicationId);
    }

    private GeneratedAsset(AssetKind kind, String inputKey, AudioGenerator.GeneratedBinary binary,
            String objectKey, UUID sourceRevisionId, UUID sourcePublicationId) {
        this.kind = kind;
        this.inputKey = inputKey;
        this.mediaType = binary.mediaType();
        this.byteSize = binary.bytes().length;
        this.objectKey = objectKey;
        this.generator = binary.generator();
        this.sourceRevisionId = sourceRevisionId;
        this.sourcePublicationId = sourcePublicationId;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public AssetKind getKind() { return kind; }
    public String getInputKey() { return inputKey; }
    public String getMediaType() { return mediaType; }
    public long getByteSize() { return byteSize; }
    public String getObjectKey() { return objectKey; }
    public String getGenerator() { return generator; }
    public UUID getSourceRevisionId() { return sourceRevisionId; }
    public UUID getSourcePublicationId() { return sourcePublicationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
