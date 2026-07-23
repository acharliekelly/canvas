package me.acharliekelly.canvas.caption.api;

import java.time.Instant;
import java.util.UUID;
import me.acharliekelly.canvas.caption.CaptionJob;

public record CaptionJobResponse(
        UUID jobId,
        UUID artworkId,
        /** Terminal states are {@code SUCCEEDED} and {@code FAILED}; polling may stop then. */
        CaptionJob.State state,
        /** Counts the initial request or a retry created after failure, not runner claims. */
        int attemptCount,
        /** A sanitized failure explanation; worker and executor internals are never returned. */
        String errorMessage,
        /** The generated draft retained by a successful terminal job, or {@code null} otherwise. */
        UUID resultingDescriptionId,
        long version,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt) {

    public static CaptionJobResponse from(CaptionJob job) {
        return new CaptionJobResponse(job.getId(), job.getArtwork().getId(), job.getState(),
                job.getAttemptCount(), job.getErrorMessage(), job.getResultingDescriptionId(),
                job.getVersion(), job.getCreatedAt(), job.getStartedAt(), job.getCompletedAt(), job.getUpdatedAt());
    }
}
