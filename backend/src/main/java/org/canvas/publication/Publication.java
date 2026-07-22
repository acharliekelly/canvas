package org.canvas.publication;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.canvas.artwork.Artwork;

@Entity
@Table(name = "publications")
public class Publication {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artwork_id", nullable = false)
    private Artwork artwork;

    @Column(name = "publication_version", nullable = false)
    private int publicationVersion;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "current_artwork_id", unique = true)
    private UUID currentArtworkId;

    @Column(name = "snapshot_title", nullable = false)
    private String title;

    @Column(name = "snapshot_credit", nullable = false)
    private String credit;

    @Column(name = "snapshot_image_object_key", nullable = false, length = 512)
    private String imageObjectKey;

    @Column(name = "snapshot_image_media_type", nullable = false, length = 64)
    private String imageMediaType;

    @Column(name = "snapshot_image_byte_size", nullable = false)
    private long imageByteSize;

    @Column(name = "published_by", nullable = false)
    private UUID publishedBy;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @OneToMany(mappedBy = "publication", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<PublishedDescription> descriptions = new ArrayList<>();

    protected Publication() {
    }

    Publication(Artwork artwork, int publicationVersion, String contentHash, UUID publishedBy, Instant publishedAt) {
        this.artwork = artwork;
        this.publicationVersion = publicationVersion;
        this.contentHash = contentHash;
        this.title = artwork.getTitle();
        this.credit = artwork.getCredit();
        this.imageObjectKey = artwork.getImageObjectKey();
        this.imageMediaType = artwork.getMediaType();
        this.imageByteSize = artwork.getByteSize();
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
    }

    void addDescription(UUID approvedRevisionId, int displayOrder, String label, String text) {
        descriptions.add(new PublishedDescription(this, approvedRevisionId, displayOrder, label, text));
    }

    void markCurrent() {
        currentArtworkId = artwork.getId();
    }

    void markSuperseded() {
        currentArtworkId = null;
    }

    public UUID getId() { return id; }
    public Artwork getArtwork() { return artwork; }
    public int getPublicationVersion() { return publicationVersion; }
    public String getContentHash() { return contentHash; }
    public boolean isCurrent() { return currentArtworkId != null; }
    public String getTitle() { return title; }
    public String getCredit() { return credit; }
    public String getImageObjectKey() { return imageObjectKey; }
    public String getImageMediaType() { return imageMediaType; }
    public long getImageByteSize() { return imageByteSize; }
    public UUID getPublishedBy() { return publishedBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public List<PublishedDescription> getDescriptions() { return Collections.unmodifiableList(descriptions); }
}
