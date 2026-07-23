package me.acharliekelly.canvas.publication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import me.acharliekelly.canvas.artwork.ArtworkRepository;
import me.acharliekelly.canvas.description.DescriptionRepository;
import me.acharliekelly.canvas.publication.asset.GeneratedAssetRepository;
import me.acharliekelly.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicArtworkApiTest {
    private static final byte[] IMAGE = imageBytes();

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    PublicationRepository publicationRepository;

    @Autowired
    DescriptionRepository descriptionRepository;

    @Autowired
    ArtworkRepository artworkRepository;

    @Autowired
    GeneratedAssetRepository assetRepository;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean(name = "originalObjectStorage")
    ObjectStorage originalStorage;

    @MockitoBean(name = "generatedObjectStorage")
    ObjectStorage generatedStorage;

    @BeforeEach
    void cleanDatabase() {
        publicationRepository.deleteAll();
        assetRepository.deleteAll();
        descriptionRepository.deleteAll();
        artworkRepository.deleteAll();
        when(originalStorage.put(any(), anyLong(), anyString()))
                .thenAnswer(invocation -> new ObjectStorage.StoredObject("originals/" + UUID.randomUUID()));
        when(generatedStorage.putGenerated(anyString(), any(), anyLong(), anyString()))
                .thenAnswer(invocation -> new ObjectStorage.StoredObject(invocation.getArgument(0)));
        when(originalStorage.get(anyString())).thenReturn(new ByteArrayInputStream(IMAGE));
    }

    @AfterEach
    void removePublicationSnapshots() {
        publicationRepository.deleteAll();
    }

    @Test
    void publicArtworkIsNotFoundBeforePublicationAndDoesNotRequireAuthentication() throws Exception {
        mvc.perform(get("/public/artworks/not-published"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void publicationRequiresAuthenticationCsrfAndTheCurrentArtworkVersion() throws Exception {
        JsonNode artwork = uploadArtwork("Blue Study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());

        mvc.perform(post("/api/artworks/{artworkId}/publication", artworkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}").with(csrf()))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/artworks/{artworkId}/publication", artworkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}").with(user("admin")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/artworks/{artworkId}/publication", artworkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":99}").with(user("admin")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale_version"));
    }

    @Test
    void draftOnlyPublicationReturnsAnActionableProblem() throws Exception {
        JsonNode artwork = uploadArtwork("Blue Study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        createDescription(artworkId, "Objective", "A private draft.");

        mvc.perform(post("/api/artworks/{artworkId}/publication", artworkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + artworkVersion(artworkId) + "}")
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("publication_not_allowed"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(
                        "Approve at least one description")));
    }

    @Test
    void publicJsonContainsOnlySnapshotContentAndPublicImageUrl() throws Exception {
        JsonNode artwork = uploadArtwork("Blue Study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        JsonNode objective = createDescription(artworkId, "Objective", "A blue square.");
        approveDescription(artworkId, objective);
        createDescription(artworkId, "Editorial draft", "Internal draft text.");

        String publishedBody = mvc.perform(post("/api/artworks/{artworkId}/publication", artworkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + artworkVersion(artworkId) + "}")
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String slug = json.readTree(publishedBody).get("slug").asText();

        mvc.perform(get("/public/artworks/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Blue Study"))
                .andExpect(jsonPath("$.credit").value("A. Artist"))
                .andExpect(jsonPath("$.imageUrl").value("/public/artworks/" + slug + "/image"))
                .andExpect(jsonPath("$.qrUrl").value(org.hamcrest.Matchers.matchesPattern(
                        "/public/artworks/" + slug + "/qr/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.descriptions.length()").value(1))
                .andExpect(jsonPath("$.descriptions[0].label").value("Objective"))
                .andExpect(jsonPath("$.descriptions[0].text").value("A blue square."))
                .andExpect(jsonPath("$.descriptions[0].audioUrl").value(
                        org.hamcrest.Matchers.matchesPattern("/public/artworks/" + slug
                                + "/descriptions/[0-9a-f-]{36}/audio/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.artworkId").doesNotExist())
                .andExpect(jsonPath("$.publicationId").doesNotExist())
                .andExpect(jsonPath("$.objectKey").doesNotExist())
                .andExpect(jsonPath("$.descriptions[0].revisionId").doesNotExist())
                .andExpect(jsonPath("$.descriptions[0].approvedBy").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "Internal draft text"))));

        mvc.perform(get("/public/artworks/{slug}/image", slug))
                .andExpect(status().isOk())
                .andExpect(content().bytes(IMAGE))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().longValue("Content-Length", IMAGE.length));
    }

    @Test
    void laterDraftDoesNotChangePublishedSnapshotUntilItsApprovalAndRepublish() throws Exception {
        JsonNode artwork = uploadArtwork("Blue Study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        JsonNode created = createDescription(artworkId, "Objective", "Original approved text.");
        JsonNode approved = approveDescription(artworkId, created);
        String slug = publish(artworkId);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/artworks/{artworkId}/descriptions/{descriptionId}/draft", artworkId,
                        created.get("descriptionId").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Objective\",\"text\":\"Private new draft.\",\"version\":"
                                + approved.get("version").asLong() + "}")
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(get("/public/artworks/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descriptions[0].text").value("Original approved text."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "Private new draft"))));
    }

    @Test
    void legacySnapshotWithoutAnUnambiguousAudioAssociationRemainsAvailableAsText() throws Exception {
        JsonNode artwork = uploadArtwork("Legacy Study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        approveDescription(artworkId, createDescription(artworkId, "Objective", "A blue square."));
        String slug = publish(artworkId);
        jdbc.update("UPDATE published_descriptions SET audio_asset_id = NULL");
        jdbc.update("UPDATE publications SET qr_asset_id = NULL");

        mvc.perform(get("/public/artworks/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.descriptions[0].text").value("A blue square."))
                .andExpect(jsonPath("$.descriptions[0].audioUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    private String publish(UUID artworkId) throws Exception {
        String response = mvc.perform(post("/api/artworks/{artworkId}/publication", artworkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + artworkVersion(artworkId) + "}")
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("slug").asText();
    }

    private JsonNode approveDescription(UUID artworkId, JsonNode description) throws Exception {
        String response = mvc.perform(post(
                        "/api/artworks/{artworkId}/descriptions/{descriptionId}/approve", artworkId,
                        description.get("descriptionId").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + description.get("version").asLong() + "}")
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private JsonNode createDescription(UUID artworkId, String label, String text) throws Exception {
        String response = mvc.perform(post("/api/artworks/{artworkId}/descriptions", artworkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateRequest(label, text)))
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private JsonNode uploadArtwork(String title) throws Exception {
        String response = mvc.perform(multipart("/api/artworks")
                        .file(validPng()).param("title", title).param("credit", "A. Artist")
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private long artworkVersion(UUID artworkId) throws Exception {
        String response = mvc.perform(get("/api/artworks/{artworkId}", artworkId).with(user("admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("version").asLong();
    }

    private static MockMultipartFile validPng() throws Exception {
        return new MockMultipartFile("image", "art.png", "image/png", IMAGE);
    }

    private static byte[] imageBytes() {
        try {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        } catch (java.io.IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private record CreateRequest(String label, String text) {}
}
