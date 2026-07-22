package org.canvas.caption;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.canvas.caption.CaptionClient.CaptionRequest;
import org.canvas.caption.CaptionClient.CaptionResponse;
import org.canvas.description.DescriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class CaptionJobRunner {
    static final String SAFE_FAILURE = "Placeholder generation could not be completed. Please retry.";
    private static final Logger log = LoggerFactory.getLogger(CaptionJobRunner.class);

    private final CaptionJobRepository repository;
    private final CaptionClient client;
    private final DescriptionService descriptions;
    private final TransactionTemplate transactions;
    private final Executor executor;

    CaptionJobRunner(CaptionJobRepository repository, CaptionClient client,
            DescriptionService descriptions, PlatformTransactionManager transactionManager,
            @Qualifier("captionTaskExecutor") Executor executor) {
        this.repository = repository;
        this.client = client;
        this.descriptions = descriptions;
        this.transactions = new TransactionTemplate(transactionManager);
        this.executor = executor;
    }

    void submit(UUID jobId) {
        try {
            executor.execute(() -> run(jobId));
        } catch (java.util.concurrent.RejectedExecutionException error) {
            log.warn("Caption job rejected by bounded executor jobId={}", jobId);
            fail(jobId);
        }
    }

    public void run(UUID jobId) {
        ClaimedJob claimed = transactions.execute(status -> claim(jobId));
        if (claimed == null) {
            return;
        }
        log.info("Caption job started jobId={} artworkId={}", jobId, claimed.artworkId());
        try {
            CaptionResponse response = requireValid(client.caption(claimed.request()));
            UUID descriptionId = transactions.execute(status -> complete(jobId, response));
            if (descriptionId != null) {
                log.info("Caption job succeeded jobId={} artworkId={} descriptionId={}",
                        jobId, claimed.artworkId(), descriptionId);
            }
        } catch (Exception error) {
            log.warn("Caption job failed jobId={} artworkId={} errorType={}",
                    jobId, claimed.artworkId(), error.getClass().getSimpleName());
            fail(jobId);
        }
    }

    private ClaimedJob claim(UUID jobId) {
        CaptionJob job = repository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getState() != CaptionJob.State.PENDING) {
            return null;
        }
        job.start(Instant.now());
        repository.saveAndFlush(job);
        var artwork = job.getArtwork();
        String imageUrl = "http://backend/internal/artworks/" + artwork.getId() + "/image";
        return new ClaimedJob(artwork.getId(), new CaptionRequest(
                imageUrl, artwork.getTitle(), artwork.getCredit(), artwork.getContext()));
    }

    private UUID complete(UUID jobId, CaptionResponse response) {
        CaptionJob job = repository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getState() != CaptionJob.State.RUNNING) {
            return null;
        }
        var generated = descriptions.createGeneratedDraft(
                job.getArtwork().getId(), response.label(), response.text());
        job.succeed(generated.descriptionId(), Instant.now());
        repository.saveAndFlush(job);
        return generated.descriptionId();
    }

    private void fail(UUID jobId) {
        transactions.executeWithoutResult(status -> {
            CaptionJob job = repository.findByIdForUpdate(jobId).orElse(null);
            if (job != null) {
                job.fail(SAFE_FAILURE, Instant.now());
                repository.saveAndFlush(job);
            }
        });
    }

    private static CaptionResponse requireValid(CaptionResponse response) {
        if (response == null || response.label() == null || response.label().isBlank()
                || response.text() == null || response.text().isBlank()
                || !"deterministic-placeholder".equals(response.engine())
                || !"1".equals(response.engineVersion())) {
            throw new IllegalStateException("Caption worker returned an invalid response.");
        }
        return response;
    }

    private record ClaimedJob(UUID artworkId, CaptionRequest request) {}
}

@Configuration
class CaptionExecutionConfiguration {
    @Bean(name = "captionTaskExecutor")
    ThreadPoolTaskExecutor captionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("caption-job-");
        executor.initialize();
        return executor;
    }
}
