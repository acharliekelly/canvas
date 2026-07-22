package org.canvas.caption.api;

import java.time.Instant;
import java.util.UUID;
import org.canvas.caption.CaptionJob;

public record CaptionJobResponse(
        UUID jobId,
        UUID artworkId,
        CaptionJob.State state,
        int attemptCount,
        String errorMessage,
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
