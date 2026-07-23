package me.acharliekelly.canvas.caption;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import me.acharliekelly.canvas.caption.CaptionClient.CaptionRequest;
import me.acharliekelly.canvas.caption.CaptionClient.CaptionResponse;
import me.acharliekelly.canvas.description.DescriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs persistent caption jobs as claim, external-call, and finalization phases. Each database
 * phase uses a separate transaction, so worker network latency never holds a database lock.
 * Generated text is saved only as an unapproved draft; this runner never approves or publishes it.
 */
@Component
public class CaptionJobRunner {
    static final String SAFE_FAILURE = "Placeholder generation could not be completed. Please retry.";
    private static final Logger log = LoggerFactory.getLogger(CaptionJobRunner.class);

    private final CaptionJobRepository repository;
    private final CaptionClient client;
    private final DescriptionService descriptions;
    private final TransactionTemplate transactions;
    private final TransactionTemplate independentTransactions;
    private final Executor executor;

    CaptionJobRunner(CaptionJobRepository repository, CaptionClient client,
            DescriptionService descriptions, PlatformTransactionManager transactionManager,
            @Qualifier("captionTaskExecutor") Executor executor) {
        this.repository = repository;
        this.client = client;
        this.descriptions = descriptions;
        this.transactions = new TransactionTemplate(transactionManager);
        this.independentTransactions = new TransactionTemplate(transactionManager);
        this.independentTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.executor = executor;
    }

    void submit(UUID jobId) {
        try {
            executor.execute(() -> run(jobId));
        } catch (java.util.concurrent.RejectedExecutionException error) {
            log.warn("Caption job rejected by bounded executor jobId={}", jobId, error);
            reject(jobId);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void recoverIncompleteJobs() {
        List<UUID> jobIds = transactions.execute(status -> {
            List<CaptionJob> incomplete = repository.findAllByStateInForUpdate(
                    List.of(CaptionJob.State.PENDING, CaptionJob.State.RUNNING));
            Instant now = Instant.now();
            // Pending work is requeued unchanged; abandoned running work becomes pending again.
            // Attempt counts identify retry jobs created after failure, not individual claims.
            incomplete.forEach(job -> job.resetForRecovery(now));
            repository.flush();
            return incomplete.stream().map(CaptionJob::getId).toList();
        });
        if (jobIds == null || jobIds.isEmpty()) {
            return;
        }
        log.info("Recovering incomplete caption jobs count={}", jobIds.size());
        jobIds.forEach(this::submit);
    }

    public void run(UUID jobId) {
        // Claim and terminal transitions are isolated from the external call in separate transactions.
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
                    jobId, claimed.artworkId(), error.getClass().getSimpleName(), error);
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
        // The terminal job retains this generated-draft link for later polling and audit.
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

    private void reject(UUID jobId) {
        independentTransactions.executeWithoutResult(status -> {
            CaptionJob job = repository.findByIdForUpdate(jobId).orElse(null);
            if (job != null && job.rejectIfPending(SAFE_FAILURE, Instant.now())) {
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
