package org.canvas.caption;

import java.util.UUID;
import org.canvas.artwork.Artwork;
import org.canvas.artwork.ArtworkRepository;
import org.canvas.caption.api.CaptionJobResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CaptionJobService {
    private final CaptionJobRepository repository;
    private final ArtworkRepository artworkRepository;
    private final CaptionJobRunner runner;
    private final boolean autoSubmit;

    CaptionJobService(CaptionJobRepository repository, ArtworkRepository artworkRepository,
            CaptionJobRunner runner,
            @Value("${canvas.caption-auto-submit:true}") boolean autoSubmit) {
        this.repository = repository;
        this.artworkRepository = artworkRepository;
        this.runner = runner;
        this.autoSubmit = autoSubmit;
    }

    @Transactional
    public CaptionJobResponse request(UUID artworkId) {
        Artwork artwork = artworkRepository.findByIdForUpdate(artworkId)
                .orElseThrow(() -> new CaptionProblem("artwork_not_found", "Artwork was not found."));
        CaptionJob latest = repository.findTopByArtworkIdOrderByAttemptCountDesc(artworkId).orElse(null);
        CaptionJob selected;
        boolean needsSubmission = false;
        if (latest == null) {
            selected = repository.saveAndFlush(new CaptionJob(artwork, 1));
            needsSubmission = true;
        } else if (latest.getState() == CaptionJob.State.FAILED) {
            selected = repository.saveAndFlush(new CaptionJob(artwork, latest.getAttemptCount() + 1));
            needsSubmission = true;
        } else {
            selected = latest;
        }
        if (autoSubmit && needsSubmission) {
            submitAfterCommit(selected.getId());
        }
        return CaptionJobResponse.from(selected);
    }

    @Transactional(readOnly = true)
    public CaptionJobResponse get(UUID artworkId, UUID jobId) {
        return repository.findByIdAndArtworkId(jobId, artworkId)
                .map(CaptionJobResponse::from)
                .orElseThrow(() -> new CaptionProblem("caption_job_not_found", "Caption job was not found."));
    }

    private void submitAfterCommit(UUID jobId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runner.submit(jobId);
            }
        });
    }

    public static class CaptionProblem extends RuntimeException {
        private final String code;

        CaptionProblem(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() { return code; }
    }
}
