package org.canvas.description;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.imageio.ImageIO;
import org.canvas.artwork.ArtworkRepository;
import org.canvas.artwork.ArtworkService;
import org.canvas.artwork.api.ArtworkDetail;
import org.canvas.description.DescriptionService.DescriptionProblem;
import org.canvas.description.api.DescriptionResponse;
import org.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DescriptionPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("canvas")
            .withUsername("canvas")
            .withPassword("canvas-test-password");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired DescriptionService service;
    @Autowired ArtworkService artworkService;
    @Autowired ArtworkRepository artworkRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @MockitoBean ObjectStorage storage;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM artworks");
        when(storage.put(any(), anyLong(), anyString()))
                .thenAnswer(invocation -> new ObjectStorage.StoredObject("originals/" + UUID.randomUUID()));
    }

    @Test
    void concurrentCreatesHaveContiguousUniqueOrderAndAdvanceArtworkVersion() throws Exception {
        ArtworkDetail artwork = createArtwork("Concurrent descriptions");
        int descriptionCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(descriptionCount);
        CountDownLatch ready = new CountDownLatch(descriptionCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DescriptionResponse>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < descriptionCount; index++) {
                int labelNumber = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    return service.createManual(artwork.id(), "Label " + labelNumber, "Text " + labelNumber);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<DescriptionResponse> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        List<DescriptionResponse> saved = service.listForArtwork(artwork.id());
        assertThat(saved).hasSize(descriptionCount);
        assertThat(saved).extracting(DescriptionResponse::displayOrder)
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        assertThat(saved).extracting(response -> response.currentRevision().label())
                .containsExactlyInAnyOrder("Label 0", "Label 1", "Label 2", "Label 3",
                        "Label 4", "Label 5", "Label 6", "Label 7");
        assertThat(artworkRepository.findById(artwork.id()).orElseThrow().getVersion())
                .isEqualTo(artwork.version() + descriptionCount);
    }

    @Test
    void creationSerializesWithReorderAndMakesItsLoadedVersionStale() throws Exception {
        ArtworkDetail artwork = createArtwork("Create versus reorder");
        DescriptionResponse objective = service.createManual(artwork.id(), "Objective", "First.");
        long versionBeforeConcurrentCreate = artworkRepository.findById(artwork.id()).orElseThrow().getVersion();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch createdInsideTransaction = new CountDownLatch(1);
        CountDownLatch allowCreateCommit = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Future<?> create = executor.submit(() -> transaction.executeWithoutResult(ignored -> {
            service.createManual(artwork.id(), "Subjective", "Second.");
            createdInsideTransaction.countDown();
            await(allowCreateCommit);
        }));

        try {
            assertThat(createdInsideTransaction.await(10, TimeUnit.SECONDS)).isTrue();
            Future<List<DescriptionResponse>> reorder = executor.submit(() -> service.reorder(
                    artwork.id(), List.of(objective.descriptionId()), versionBeforeConcurrentCreate));

            assertThatThrownBy(() -> reorder.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(TimeoutException.class);
            allowCreateCommit.countDown();
            create.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> reorder.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOf(DescriptionProblem.class)
                    .extracting("code").isEqualTo("stale_version");
        } finally {
            allowCreateCommit.countDown();
            executor.shutdownNow();
        }

        assertThat(service.listForArtwork(artwork.id()))
                .extracting(DescriptionResponse::displayOrder)
                .containsExactly(0, 1);
    }

    @Test
    void databaseRejectsCurrentRevisionOwnedByAnotherDescription() throws Exception {
        ArtworkDetail artwork = createArtwork("Current revision ownership");
        DescriptionResponse first = service.createManual(artwork.id(), "First", "First text.");
        DescriptionResponse second = service.createManual(artwork.id(), "Second", "Second text.");
        DescriptionResponse approved = service.approve(
                artwork.id(), second.descriptionId(), "admin", second.version());
        service.updateDraft(artwork.id(), second.descriptionId(), "Second", "New second text.", approved.version());

        assertThatThrownBy(() -> jdbc.update("UPDATE descriptions SET current_revision_id = ? WHERE id = ?",
                approved.approvedRevisionId(), first.descriptionId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsParentRevisionOwnedByAnotherDescription() throws Exception {
        ArtworkDetail artwork = createArtwork("Parent revision ownership");
        DescriptionResponse first = service.createManual(artwork.id(), "First", "First text.");
        DescriptionResponse second = service.createManual(artwork.id(), "Second", "Second text.");

        assertThatThrownBy(() -> jdbc.update("UPDATE description_revisions SET parent_revision_id = ? WHERE id = ?",
                second.currentRevision().revisionId(), first.currentRevision().revisionId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingArtworkStillCascadesThroughDescriptionsAndRevisions() throws Exception {
        ArtworkDetail artwork = createArtwork("Cascade cleanup");
        DescriptionResponse description = service.createManual(artwork.id(), "Objective", "First text.");
        DescriptionResponse approved = service.approve(
                artwork.id(), description.descriptionId(), "admin", description.version());
        service.updateDraft(artwork.id(), description.descriptionId(), "Objective", "Second text.", approved.version());

        assertThat(jdbc.update("DELETE FROM artworks WHERE id = ?", artwork.id())).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM descriptions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM description_revisions", Integer.class)).isZero();
    }

    private ArtworkDetail createArtwork(String title) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return artworkService.upload(new MockMultipartFile("image", "art.png", "image/png", bytes.toByteArray()),
                title, "A. Artist", null);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent test coordination.");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent test.", error);
        }
    }
}
