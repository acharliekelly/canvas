package me.acharliekelly.canvas.artwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ArtworkUploadIntegrationTest {
    private static final String BUCKET = "canvas-test-originals";
    private static final String MINIO_USER = "canvas-minio";
    private static final String MINIO_PASSWORD = "canvas-minio-test-password";

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
        properties.add("canvas.storage.endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        properties.add("canvas.storage.access-key", () -> MINIO_USER);
        properties.add("canvas.storage.secret-key", () -> MINIO_PASSWORD);
        properties.add("canvas.storage.region", () -> "us-east-1");
        properties.add("canvas.storage.originals-bucket", () -> BUCKET);
        properties.add("canvas.storage.generated-bucket", () -> "canvas-test-generated-assets");
    }

    @Autowired MockMvc mvc;
    @Autowired ArtworkRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired S3Client s3;

    @BeforeEach
    void prepareRealServices() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (S3Exception error) {
            if (error.statusCode() != 409) {
                throw error;
            }
        }
        Integer constraintCount = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'artworks_title_commit_test'", Integer.class);
        if (constraintCount != null && constraintCount == 0) {
            jdbc.execute("ALTER TABLE artworks ADD CONSTRAINT artworks_title_commit_test "
                    + "UNIQUE (title) DEFERRABLE INITIALLY DEFERRED");
        }
    }

    @AfterEach
    void cleanServices() {
        repository.deleteAll();
        storedObjectKeys().forEach(key -> s3.deleteObject(
                DeleteObjectRequest.builder().bucket(BUCKET).key(key).build()));
    }

    @Test
    void migrationsAndRealStoragePersistPngAndJpegUploads() throws Exception {
        upload(ArtworkApiTest.validPng(), "Blue Study").andExpect(status().isCreated());
        upload(ArtworkApiTest.validJpeg(), "Red Study").andExpect(status().isCreated());

        assertThat(repository.findAll()).extracting(Artwork::getTitle)
                .containsExactlyInAnyOrder("Blue Study", "Red Study");
        assertThat(storedObjectKeys()).hasSize(2).allMatch(key -> key.startsWith("artworks/"));
    }

    @Test
    void commitTimeFailureRollsBackMetadataAndDeletesTheStoredObject() throws Exception {
        upload(ArtworkApiTest.validPng(), "Duplicate title").andExpect(status().isCreated());

        upload(ArtworkApiTest.validJpeg(), "Duplicate title")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("persistence_unavailable"));

        assertThat(repository.count()).isOne();
        assertThat(storedObjectKeys()).hasSize(1);
    }

    private org.springframework.test.web.servlet.ResultActions upload(
            org.springframework.mock.web.MockMultipartFile image, String title) throws Exception {
        return mvc.perform(multipart("/api/artworks")
                .file(image)
                .param("title", title)
                .param("credit", "A. Artist")
                .with(user("admin"))
                .with(csrf()));
    }

    private List<String> storedObjectKeys() {
        return s3.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET).build())
                .contents().stream().map(object -> object.key()).toList();
    }
}
