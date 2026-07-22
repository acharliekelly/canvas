package org.canvas.description;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.canvas.artwork.Artwork;

@Entity
@Table(name = "descriptions", uniqueConstraints = {
        @UniqueConstraint(name = "descriptions_artwork_display_order_unique",
                columnNames = {"artwork_id", "display_order"})
})
public class Description {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artwork_id", nullable = false)
    private Artwork artwork;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DescriptionSource source;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_revision_id", unique = true)
    private DescriptionRevision currentRevision;

    @Column(name = "current_revision_owner_id")
    private UUID currentRevisionOwnerId;

    @OneToMany(mappedBy = "description", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("createdAt ASC, id ASC")
    private List<DescriptionRevision> revisions = new ArrayList<>();

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Description() {
    }

    Description(Artwork artwork, DescriptionSource source, int displayOrder) {
        this.artwork = artwork;
        this.source = source;
        this.displayOrder = displayOrder;
    }

    void startDraft(String label, String text, DescriptionRevision parentRevision) {
        DescriptionRevision draft = new DescriptionRevision(this, label, text, parentRevision);
        revisions.add(draft);
        currentRevision = draft;
        currentRevisionOwnerId = id;
        touch();
    }

    void updateCurrentDraft(String label, String text) {
        currentRevision.updateDraft(label, text);
        touch();
    }

    void approveCurrent(String approver, Instant approvedAt) {
        currentRevision.approve(approver, approvedAt);
        touch();
    }

    void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
        touch();
    }

    void touch() {
        updatedAt = Instant.now();
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

    public UUID getId() { return id; }
    public Artwork getArtwork() { return artwork; }
    public DescriptionSource getSource() { return source; }
    public int getDisplayOrder() { return displayOrder; }
    public DescriptionRevision getCurrentRevision() { return currentRevision; }
    public List<DescriptionRevision> getRevisions() { return Collections.unmodifiableList(revisions); }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
