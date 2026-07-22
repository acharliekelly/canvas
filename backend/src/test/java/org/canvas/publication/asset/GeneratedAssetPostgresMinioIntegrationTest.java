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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioSystem;
import org.canvas.artwork.ArtworkRepository;
import org.canvas.description.DescriptionRepository;
import org.canvas.publication.PublicationRepository;
import org.canvas.publication.PublicationService;
import org.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(GeneratedAssetPostgresMinioIntegrationTest.ConcurrentAudioConfiguration.class)
class GeneratedAssetPostgresMinioIntegrationTest {
    private static final String BUCKET = "canvas-test-originals";
    private static final String GENERATED_BUCKET = "canvas-test-generated-assets";
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
        properties.add("canvas.storage.generated-bucket", () -> GENERATED_BUCKET);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired S3Client s3;
    @Autowired JdbcTemplate jdbc;
    @Autowired PublicationService publicationService;
    @Autowired AssetService assetService;
    @Autowired CoordinatedAudioGenerator audioGenerator;
    @Autowired GeneratedAssetRepository assetRepository;
    @Autowired PublicationRepository publicationRepository;
    @Autowired DescriptionRepository descriptionRepository;
    @Autowired ArtworkRepository artworkRepository;
    @Autowired @Qualifier("originalObjectStorage") ObjectStorage originalStorage;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void prepareRealServices() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (S3Exception error) {
            if (error.statusCode() != 409) throw error;
        }
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(GENERATED_BUCKET).build());
        } catch (S3Exception error) {
            if (error.statusCode() != 409) throw error;
        }
        jdbc.execute("ALTER TABLE generated_assets DROP CONSTRAINT IF EXISTS generated_assets_kind_commit_test");
        publicationRepository.deleteAll();
        assetRepository.deleteAll();
        descriptionRepository.deleteAll();
        artworkRepository.deleteAll();
        storedObjectKeys().forEach(this::deleteObject);
        storedObjectKeys(GENERATED_BUCKET).forEach(key -> deleteObject(GENERATED_BUCKET, key));
        audioGenerator.reset();
    }

    @Test
    void concurrentAudioCacheMissesLeaveOneReadableCommittedObject() throws Exception {
        var input = new AudioGenerator.ApprovedDescriptionInput(
                UUID.fromString("fc246438-a640-4b80-992f-041779010289"),
                "Objective", "A blue square.");
        audioGenerator.coordinateNextTwoCalls();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<GeneratedAsset>> futures = new ArrayList<>();
        List<GeneratedAsset> results = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();

        try {
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(ready);
                    await(start);
                    return assetService.audioFor(input);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<GeneratedAsset> future : futures) {
                try {
                    results.add(future.get(20, TimeUnit.SECONDS));
                } catch (ExecutionException error) {
                    failures.add(error.getCause());
                }
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        GeneratedAsset committed = assetRepository.findAll().getFirst();
        byte[] storedAudio = s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(GENERATED_BUCKET).key(committed.getObjectKey()).build()).asByteArray();

        assertThat(failures).isEmpty();
        assertThat(results).hasSize(2)
                .extracting(GeneratedAsset::getId).containsOnly(committed.getId());
        assertThat(assetRepository.count()).isOne();
        assertThat(storedObjectKeys()).isEmpty();
        assertThat(storedObjectKeys(GENERATED_BUCKET)).containsExactly(committed.getObjectKey());
        assertThat(AudioSystem.getAudioInputStream(new ByteArrayInputStream(storedAudio)).getFrameLength())
                .isPositive();
        assertThat(audioGenerator.calls()).isOne();
    }

    @AfterEach
    void cleanRealServices() {
        jdbc.execute("ALTER TABLE generated_assets DROP CONSTRAINT IF EXISTS generated_assets_kind_commit_test");
        publicationRepository.deleteAll();
        assetRepository.deleteAll();
        descriptionRepository.deleteAll();
        artworkRepository.deleteAll();
        storedObjectKeys().forEach(this::deleteObject);
        storedObjectKeys(GENERATED_BUCKET).forEach(key -> deleteObject(GENERATED_BUCKET, key));
    }

    @Test
    void v8PersistsAssociatedPlayableAndScannableAssetsAndReusesStableContentAddresses() throws Exception {
        JsonNode artwork = uploadArtwork("Blue Study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        JsonNode approved = approve(artworkId,
                createDescription(artworkId, "Objective", "A blue square."));
        JsonNode publication = publishResult(artworkId);
        String slug = publication.get("slug").asText();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '8' AND success", Integer.class))
                .isOne();
        assertThat(assetRepository.findAll()).hasSize(2)
                .extracting(GeneratedAsset::getKind).containsExactlyInAnyOrder(AssetKind.AUDIO, AssetKind.QR_CODE);
        assertThat(storedObjectKeys(BUCKET)).singleElement()
                .matches(key -> key.startsWith("artworks/"));
        assertThat(storedObjectKeys(GENERATED_BUCKET)).hasSize(2)
                .anyMatch(key -> key.matches("generated/audio/[0-9a-f]{64}\\.wav"))
                .anyMatch(key -> key.matches("generated/qr/[0-9a-f]{64}\\.png"));

        String body = mvc.perform(get("/public/artworks/{slug}", slug))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String audioUrl = json.readTree(body).get("descriptions").get(0).get("audioUrl").asText();
        byte[] audio = mvc.perform(get(audioUrl)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(AudioSystem.getAudioInputStream(new ByteArrayInputStream(audio)).getFrameLength()).isPositive();
        byte[] qr = mvc.perform(get(publication.get("qrUrl").asText())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(QrTestDecoder.decode(qr)).isEqualTo(PUBLIC_BASE + "/artworks/" + slug);

        publish(artworkId);
        assertThat(assetRepository.count()).isEqualTo(2);
        assertThat(storedObjectKeys(BUCKET)).hasSize(1);
        assertThat(storedObjectKeys(GENERATED_BUCKET)).hasSize(2);

        JsonNode draft = updateDraft(artworkId, approved, "A cobalt square.");
        approve(artworkId, draft);
        publish(artworkId);
        assertThat(assetRepository.findAll()).hasSize(3);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM generated_assets WHERE kind = 'AUDIO'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM generated_assets WHERE kind = 'QR_CODE'", Integer.class)).isOne();
        assertThat(storedObjectKeys(BUCKET)).hasSize(1);
        assertThat(storedObjectKeys(GENERATED_BUCKET)).hasSize(3);
    }

    @Test
    void routesOriginalArtworkAndGeneratedPublicationAssetsToSeparateBuckets() throws Exception {
        JsonNode artwork = uploadArtwork("Bucket routing study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        approve(artworkId, createDescription(artworkId, "Objective", "A blue square."));

        publish(artworkId);

        assertThat(storedObjectKeys(BUCKET)).singleElement()
                .matches(key -> key.startsWith("artworks/"));
        assertThat(storedObjectKeys(GENERATED_BUCKET)).hasSize(2)
                .anyMatch(key -> key.matches("generated/audio/[0-9a-f]{64}\\.wav"))
                .anyMatch(key -> key.matches("generated/qr/[0-9a-f]{64}\\.png"));
    }

    @Test
    void republishingRepairsADeletedMinioObjectWithoutDuplicateMetadata() throws Exception {
        JsonNode artwork = uploadArtwork("Repair study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        approve(artworkId, createDescription(artworkId, "Objective", "A blue square."));
        String slug = publish(artworkId);
        String publicBody = mvc.perform(get("/public/artworks/{slug}", slug))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String audioUrl = json.readTree(publicBody).get("descriptions").get(0).get("audioUrl").asText();
        GeneratedAsset audio = assetRepository.findAll().stream()
                .filter(asset -> asset.getKind() == AssetKind.AUDIO).findFirst().orElseThrow();
        deleteObject(GENERATED_BUCKET, audio.getObjectKey());

        publish(artworkId);

        assertThat(assetRepository.count()).isEqualTo(2);
        assertThat(storedObjectKeys(GENERATED_BUCKET)).contains(audio.getObjectKey());
        byte[] repaired = mvc.perform(get(audioUrl)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(AudioSystem.getAudioInputStream(new ByteArrayInputStream(repaired)).getFrameLength())
                .isPositive();
    }

    @Test
    void persistedAssetUrlsSurviveGeneratorNamespaceAndPublicBaseConfigurationChanges() throws Exception {
        JsonNode artwork = uploadArtwork("Configuration stability study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        approve(artworkId, createDescription(artworkId, "Objective", "A blue square."));
        JsonNode publication = publishResult(artworkId);
        String slug = publication.get("slug").asText();
        String publicBody = mvc.perform(get("/public/artworks/{slug}", slug))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String audioUrl = json.readTree(publicBody).get("descriptions").get(0).get("audioUrl").asText();
        UUID publishedDescriptionId = UUID.fromString(audioUrl.split("/")[5]);
        UUID audioAssetId = UUID.fromString(audioUrl.substring(audioUrl.lastIndexOf('/') + 1));
        String qrUrl = publication.get("qrUrl").asText();
        UUID qrAssetId = UUID.fromString(qrUrl.substring(qrUrl.lastIndexOf('/') + 1));

        audioGenerator.changeNamespace("replacement-audio-v2");
        PublicationService changedConfiguration = new PublicationService(publicationRepository,
                artworkRepository, descriptionRepository, originalStorage, assetService,
                java.net.URI.create("https://replacement.example/new-base"));

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        PublicationService.PublicAsset persistedAudio = transaction.execute(ignored ->
                changedConfiguration.publicAudio(slug, publishedDescriptionId, audioAssetId));
        PublicationService.PublicAsset persistedQr = transaction.execute(ignored ->
                changedConfiguration.publicQr(slug, qrAssetId));
        byte[] audio = java.util.Objects.requireNonNull(persistedAudio).content().readAllBytes();
        byte[] qr = java.util.Objects.requireNonNull(persistedQr).content().readAllBytes();

        assertThat(AudioSystem.getAudioInputStream(new ByteArrayInputStream(audio)).getFrameLength()).isPositive();
        assertThat(QrTestDecoder.decode(qr)).isEqualTo(PUBLIC_BASE + "/artworks/" + slug);
        assertThat(audioGenerator.calls()).isOne();
    }

    @Test
    void commitFailureDeletesNewMinioObjectAndKeepsPriorPublicationCurrent() throws Exception {
        JsonNode artwork = uploadArtwork("Rollback Study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        JsonNode approved = approve(artworkId,
                createDescription(artworkId, "Objective", "Original approved text."));
        String slug = publish(artworkId);
        List<String> originalObjects = storedObjectKeys(BUCKET);
        List<String> originalGeneratedObjects = storedObjectKeys(GENERATED_BUCKET);
        JsonNode draft = updateDraft(artworkId, approved, "Replacement approved text.");
        approve(artworkId, draft);
        jdbc.execute("ALTER TABLE generated_assets ADD CONSTRAINT generated_assets_kind_commit_test "
                + "UNIQUE (kind) DEFERRABLE INITIALLY DEFERRED");

        assertThatThrownBy(() -> publicationService.publish(
                artworkId, artworkRepository.findById(artworkId).orElseThrow().getVersion(), ADMIN_ID))
                .isInstanceOf(RuntimeException.class);

        assertThat(storedObjectKeys(BUCKET)).containsExactlyInAnyOrderElementsOf(originalObjects);
        assertThat(storedObjectKeys(GENERATED_BUCKET))
                .containsExactlyInAnyOrderElementsOf(originalGeneratedObjects);
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
        return publishResult(artworkId).get("slug").asText();
    }

    private JsonNode publishResult(UUID artworkId) throws Exception {
        String response = mvc.perform(post("/api/artworks/{artworkId}/publication", artworkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + artworkRepository.findById(artworkId).orElseThrow().getVersion()
                                + "}")
                .with(user("admin")).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private List<String> storedObjectKeys() {
        return storedObjectKeys(BUCKET);
    }

    private List<String> storedObjectKeys(String bucket) {
        return s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build())
                .contents().stream().map(object -> object.key()).sorted().toList();
    }

    private void deleteObject(String key) {
        deleteObject(BUCKET, key);
    }

    private void deleteObject(String bucket, String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent asset test coordination.");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent asset test.", error);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConcurrentAudioConfiguration {
        @Bean
        @Primary
        CoordinatedAudioGenerator coordinatedAudioGenerator() {
            return new CoordinatedAudioGenerator();
        }
    }

    static final class CoordinatedAudioGenerator implements AudioGenerator {
        private final PlaceholderAudioGenerator delegate = new PlaceholderAudioGenerator();
        private final AtomicInteger calls = new AtomicInteger();
        private volatile CountDownLatch generationBarrier;
        private volatile String cacheNamespace = "placeholder-audio-v1";

        @Override
        public String cacheNamespace() {
            return cacheNamespace;
        }

        @Override
        public GeneratedBinary generate(ApprovedDescriptionInput input) {
            calls.incrementAndGet();
            CountDownLatch barrier = generationBarrier;
            if (barrier != null) {
                barrier.countDown();
                awaitCompetingGeneration(barrier);
            }
            return delegate.generate(input);
        }

        void coordinateNextTwoCalls() {
            generationBarrier = new CountDownLatch(2);
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
            generationBarrier = null;
            cacheNamespace = "placeholder-audio-v1";
        }

        void changeNamespace(String replacement) {
            cacheNamespace = replacement;
        }

        private static void awaitCompetingGeneration(CountDownLatch barrier) {
            try {
                barrier.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while coordinating concurrent asset generation.", error);
            }
        }
    }
}
