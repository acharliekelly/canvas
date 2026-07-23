package me.acharliekelly.canvas.description;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "description_revisions")
public class DescriptionRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "description_id", nullable = false)
    private Description description;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RevisionState state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_revision_id")
    private DescriptionRevision parentRevision;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DescriptionRevision() {
    }

    DescriptionRevision(Description description, String label, String text, DescriptionRevision parentRevision) {
        this.description = description;
        this.label = label;
        this.text = text;
        this.state = RevisionState.DRAFT;
        this.parentRevision = parentRevision;
    }

    void updateDraft(String label, String text) {
        if (state != RevisionState.DRAFT) {
            throw new IllegalStateException("Approved revisions are immutable.");
        }
        this.label = label;
        this.text = text;
    }

    void approve(String approver, Instant approvedAt) {
        if (state != RevisionState.DRAFT) {
            throw new IllegalStateException("Only draft revisions can be approved.");
        }
        this.state = RevisionState.APPROVED;
        this.approvedBy = approver;
        this.approvedAt = approvedAt;
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
    public String getLabel() { return label; }
    public String getText() { return text; }
    public RevisionState getState() { return state; }
    public DescriptionRevision getParentRevision() { return parentRevision; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
