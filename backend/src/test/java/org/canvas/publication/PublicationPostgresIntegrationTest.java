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

    @MockitoBean ObjectStorage storage;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM artworks");
        when(storage.put(any(), anyLong(), anyString()))
                .thenAnswer(invocation -> new ObjectStorage.StoredObject("originals/" + UUID.randomUUID()));
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

    private ArtworkDetail createArtwork() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return artworkService.upload(new MockMultipartFile(
                "image", "art.png", "image/png", bytes.toByteArray()), "Blue Study", "A. Artist", null);
    }
}
