package org.canvas.publication.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sound.sampled.AudioSystem;
import org.canvas.artwork.ArtworkRepository;
import org.canvas.description.DescriptionRepository;
import org.canvas.publication.PublicationRepository;
import org.canvas.publication.PublicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class GeneratedAssetPostgresMinioIntegrationTest {
    private static final String BUCKET = "canvas-test-generated-assets";
    private static final String MINIO_USER = "canvas-minio";
    private static final String MINIO_PASSWORD = "canvas-minio-test-password";
    private static final String PUBLIC_BASE = "https://gallery.example/access";
    private static final UUID ADMIN_ID = UUID.fromString("1945c6e9-d034-45a7-b10a-024cad3fc685");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("canvas")
            .withUsername("canvas")
            .withPassword("canvas-test-password");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(
            "minio/minio:RELEASE.2025-09-07T16-13-09Z@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e"))
            .withEnv("MINIO_ROOT_USER", MINIO_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void integrationProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("canvas.admin.username", () -> "admin");
        properties.add("canvas.admin.password-hash", () -> "{noop}password");
        properties.add("canvas.upload-max-size", () -> "1MB");
        properties.add("canvas.public-base-url", () -> PUBLIC_BASE);
        properties.add("canvas.storage.endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        properties.add("canvas.storage.access-key", () -> MINIO_USER);
        properties.add("canvas.storage.secret-key", () -> MINIO_PASSWORD);
        properties.add("canvas.storage.region", () -> "us-east-1");
        properties.add("canvas.storage.originals-bucket", () -> BUCKET);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired S3Client s3;
    @Autowired JdbcTemplate jdbc;
    @Autowired PublicationService publicationService;
    @Autowired GeneratedAssetRepository assetRepository;
    @Autowired PublicationRepository publicationRepository;
    @Autowired DescriptionRepository descriptionRepository;
    @Autowired ArtworkRepository artworkRepository;

    @BeforeEach
    void prepareRealServices() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (S3Exception error) {
            if (error.statusCode() != 409) throw error;
        }
        jdbc.execute("ALTER TABLE generated_assets DROP CONSTRAINT IF EXISTS generated_assets_kind_commit_test");
        assetRepository.deleteAll();
        publicationRepository.deleteAll();
        descriptionRepository.deleteAll();
        artworkRepository.deleteAll();
        storedObjectKeys().forEach(this::deleteObject);
    }

    @AfterEach
    void cleanRealServices() {
        jdbc.execute("ALTER TABLE generated_assets DROP CONSTRAINT IF EXISTS generated_assets_kind_commit_test");
        assetRepository.deleteAll();
        publicationRepository.deleteAll();
        descriptionRepository.deleteAll();
        artworkRepository.deleteAll();
        storedObjectKeys().forEach(this::deleteObject);
    }

    @Test
    void v7PersistsPlayableAndScannableAssetsAndReusesStableContentAddresses() throws Exception {
        JsonNode artwork = uploadArtwork("Blue Study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        JsonNode approved = approve(artworkId,
                createDescription(artworkId, "Objective", "A blue square."));
        String slug = publish(artworkId);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '7' AND success", Integer.class))
                .isOne();
        assertThat(assetRepository.findAll()).hasSize(2)
                .extracting(GeneratedAsset::getKind).containsExactlyInAnyOrder(AssetKind.AUDIO, AssetKind.QR_CODE);
        assertThat(storedObjectKeys()).hasSize(3)
                .anyMatch(key -> key.matches("generated/audio/[0-9a-f]{64}\\.wav"))
                .anyMatch(key -> key.matches("generated/qr/[0-9a-f]{64}\\.png"));

        String body = mvc.perform(get("/public/artworks/{slug}", slug))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String audioUrl = json.readTree(body).get("descriptions").get(0).get("audioUrl").asText();
        byte[] audio = mvc.perform(get(audioUrl)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(AudioSystem.getAudioInputStream(new ByteArrayInputStream(audio)).getFrameLength()).isPositive();
        byte[] qr = mvc.perform(get("/public/artworks/{slug}/qr", slug)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(QrTestDecoder.decode(qr)).isEqualTo(PUBLIC_BASE + "/artworks/" + slug);

        publish(artworkId);
        assertThat(assetRepository.count()).isEqualTo(2);
        assertThat(storedObjectKeys()).hasSize(3);

        JsonNode draft = updateDraft(artworkId, approved, "A cobalt square.");
        approve(artworkId, draft);
        publish(artworkId);
        assertThat(assetRepository.findAll()).hasSize(3);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM generated_assets WHERE kind = 'AUDIO'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM generated_assets WHERE kind = 'QR_CODE'", Integer.class)).isOne();
        assertThat(storedObjectKeys()).hasSize(4);
    }

    @Test
    void commitFailureDeletesNewMinioObjectAndKeepsPriorPublicationCurrent() throws Exception {
        JsonNode artwork = uploadArtwork("Rollback Study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        JsonNode approved = approve(artworkId,
                createDescription(artworkId, "Objective", "Original approved text."));
        String slug = publish(artworkId);
        List<String> originalObjects = storedObjectKeys();
        JsonNode draft = updateDraft(artworkId, approved, "Replacement approved text.");
        approve(artworkId, draft);
        jdbc.execute("ALTER TABLE generated_assets ADD CONSTRAINT generated_assets_kind_commit_test "
                + "UNIQUE (kind) DEFERRABLE INITIALLY DEFERRED");

        assertThatThrownBy(() -> publicationService.publish(
                artworkId, artworkRepository.findById(artworkId).orElseThrow().getVersion(), ADMIN_ID))
                .isInstanceOf(RuntimeException.class);

        assertThat(storedObjectKeys()).containsExactlyInAnyOrderElementsOf(originalObjects);
        assertThat(assetRepository.count()).isEqualTo(2);
        String publicBody = mvc.perform(get("/public/artworks/{slug}", slug))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(publicBody).get("descriptions").get(0).get("text").asText())
                .isEqualTo("Original approved text.");
    }

    private JsonNode uploadArtwork(String title) throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFElEQVR4XmNkYGD4z8DAwMDAxAADAAkEAQNRuX6QAAAAAElFTkSuQmCC");
        MockMultipartFile image = new MockMultipartFile("image", "art.png", "image/png", png);
        String response = mvc.perform(multipart("/api/artworks").file(image)
                        .param("title", title).param("credit", "A. Artist")
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private JsonNode createDescription(UUID artworkId, String label, String text) throws Exception {
        String response = mvc.perform(post("/api/artworks/{artworkId}/descriptions", artworkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("label", label, "text", text)))
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private JsonNode approve(UUID artworkId, JsonNode description) throws Exception {
        String response = mvc.perform(post(
                        "/api/artworks/{artworkId}/descriptions/{descriptionId}/approve", artworkId,
                        description.get("descriptionId").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + description.get("version").asLong() + "}")
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private JsonNode updateDraft(UUID artworkId, JsonNode approved, String text) throws Exception {
        String response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/artworks/{artworkId}/descriptions/{descriptionId}/draft", artworkId,
                        approved.get("descriptionId").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "label", "Objective", "text", text, "version", approved.get("version").asLong())))
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private String publish(UUID artworkId) throws Exception {
        String response = mvc.perform(post("/api/artworks/{artworkId}/publication", artworkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + artworkRepository.findById(artworkId).orElseThrow().getVersion()
                                + "}")
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("slug").asText();
    }

    private List<String> storedObjectKeys() {
        return s3.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET).build())
                .contents().stream().map(object -> object.key()).sorted().toList();
    }

    private void deleteObject(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(key).build());
    }
}
