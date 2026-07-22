package org.canvas.artwork;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "artworks")
public class Artwork {
    public enum LifecycleStatus { UPLOADED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String credit;
    private String context;
    @Column(name = "media_type", nullable = false)
    private String mediaType;
    @Column(name = "byte_size", nullable = false)
    private long byteSize;
    @Column(name = "object_key", nullable = false, unique = true)
    private String imageObjectKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false)
    private LifecycleStatus lifecycleStatus = LifecycleStatus.UPLOADED;
    @Column(name = "public_slug", unique = true)
    private String publicSlug;
    @Version
    private long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Artwork() {
    }

    Artwork(String title, String credit, String context, String mediaType, long byteSize, String imageObjectKey) {
        this.title = title;
        this.credit = credit;
        this.context = context;
        this.mediaType = mediaType;
        this.byteSize = byteSize;
        this.imageObjectKey = imageObjectKey;
    }

    @PrePersist
    void created() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }

    public void markDescriptionCollectionChanged() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getCredit() { return credit; }
    public String getContext() { return context; }
    public String getMediaType() { return mediaType; }
    public long getByteSize() { return byteSize; }
    public String getImageObjectKey() { return imageObjectKey; }
    public LifecycleStatus getLifecycleStatus() { return lifecycleStatus; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
