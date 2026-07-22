package org.canvas.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
import org.canvas.description.DescriptionService;
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
class PublicationPostgresIntegrationTest {
    private static final UUID ADMIN_ID = UUID.fromString("1945c6e9-d034-45a7-b10a-024cad3fc685");

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

    @Autowired PublicationService publicationService;
    @Autowired DescriptionService descriptionService;
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
        when(storage.putGenerated(anyString(), any(), anyLong(), anyString()))
                .thenAnswer(invocation -> new ObjectStorage.StoredObject(invocation.getArgument(0)));
    }

    @Test
    void v6PersistsAnApprovedRevisionSnapshotAndProtectsItsAuditReference() throws Exception {
        ArtworkDetail artwork = createArtwork();
        DescriptionResponse draft = descriptionService.createManual(
                artwork.id(), "Objective", "A blue square.");
        DescriptionResponse approved = descriptionService.approve(
                artwork.id(), draft.descriptionId(), "admin", draft.version());

        PublicationService.PublicationResult published = publicationService.publish(
                artwork.id(), artworkRepository.findById(artwork.id()).orElseThrow().getVersion(), ADMIN_ID);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '6' AND success", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM publications WHERE artwork_id = ? AND current_artwork_id = artwork_id",
                Integer.class, artwork.id())).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT approved_revision_id FROM published_descriptions WHERE publication_id = ?",
                UUID.class, published.publicationId())).isEqualTo(approved.approvedRevisionId());
        assertThat(jdbc.queryForObject("""
                SELECT confdeltype::text
                FROM pg_constraint
                WHERE conrelid = 'published_descriptions'::regclass
                  AND conname = 'published_descriptions_approved_revision_id_fkey'
                """, String.class)).isEqualTo("r");
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM description_revisions WHERE id = ?", approved.approvedRevisionId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentApprovalSerializesWithPublicationAndRejectsItsStalePreview() throws Exception {
        ArtworkDetail artwork = createArtwork();
        DescriptionResponse draft = descriptionService.createManual(
                artwork.id(), "Objective", "A blue square.");
        long previewVersion = artworkRepository.findById(artwork.id()).orElseThrow().getVersion();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch approvedInsideTransaction = new CountDownLatch(1);
        CountDownLatch allowApprovalCommit = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Future<?> approval = executor.submit(() -> transaction.executeWithoutResult(ignored -> {
            descriptionService.approve(artwork.id(), draft.descriptionId(), "admin", draft.version());
            approvedInsideTransaction.countDown();
            await(allowApprovalCommit);
        }));

        try {
            assertThat(approvedInsideTransaction.await(10, TimeUnit.SECONDS)).isTrue();
            Future<PublicationService.PublicationResult> publication = executor.submit(() ->
                    publicationService.publish(artwork.id(), previewVersion, ADMIN_ID));

            assertThatThrownBy(() -> publication.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(TimeoutException.class);
            allowApprovalCommit.countDown();
            approval.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> publication.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOf(PublicationService.PublicationProblem.class)
                    .extracting("code").isEqualTo("stale_version");
        } finally {
            allowApprovalCommit.countDown();
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM publications WHERE artwork_id = ?", Integer.class, artwork.id())).isZero();
    }

    private ArtworkDetail createArtwork() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return artworkService.upload(new MockMultipartFile(
                "image", "art.png", "image/png", bytes.toByteArray()), "Blue Study", "A. Artist", null);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test coordination.");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test coordination.", error);
        }
    }
}
