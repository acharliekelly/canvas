package me.acharliekelly.canvas.caption;

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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import me.acharliekelly.canvas.artwork.Artwork;

@Entity
@Table(name = "caption_jobs")
public class CaptionJob {
    public enum State { PENDING, RUNNING, SUCCEEDED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artwork_id", nullable = false)
    private Artwork artwork;

    @Column(name = "active_artwork_id", unique = true)
    private UUID activeArtworkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private State state = State.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "result_description_id")
    private UUID resultingDescriptionId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CaptionJob() {
    }

    CaptionJob(Artwork artwork, int attemptCount) {
        this.artwork = artwork;
        this.activeArtworkId = artwork.getId();
        this.attemptCount = attemptCount;
    }

    void start(Instant now) {
        requireState(State.PENDING);
        state = State.RUNNING;
        startedAt = now;
        touch(now);
    }

    void succeed(UUID descriptionId, Instant now) {
        requireState(State.RUNNING);
        state = State.SUCCEEDED;
        resultingDescriptionId = descriptionId;
        activeArtworkId = null;
        completedAt = now;
        touch(now);
    }

    void fail(String safeMessage, Instant now) {
        if (state != State.PENDING && state != State.RUNNING) {
            return;
        }
        state = State.FAILED;
        errorMessage = safeMessage;
        activeArtworkId = null;
        completedAt = now;
        touch(now);
    }

    boolean rejectIfPending(String safeMessage, Instant now) {
        if (state != State.PENDING) {
            return false;
        }
        fail(safeMessage, now);
        return true;
    }

    void resetForRecovery(Instant now) {
        if (state != State.RUNNING) {
            return;
        }
        state = State.PENDING;
        startedAt = null;
        touch(now);
    }

    private void requireState(State expected) {
        if (state != expected) {
            throw new IllegalStateException("Caption job cannot transition from " + state + ".");
        }
    }

    private void touch(Instant now) {
        updatedAt = now;
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
    public State getState() { return state; }
    public int getAttemptCount() { return attemptCount; }
    public String getErrorMessage() { return errorMessage; }
    public UUID getResultingDescriptionId() { return resultingDescriptionId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
