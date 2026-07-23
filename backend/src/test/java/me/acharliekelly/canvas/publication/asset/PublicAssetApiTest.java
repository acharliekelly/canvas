package me.acharliekelly.canvas.publication.asset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import me.acharliekelly.canvas.artwork.ArtworkRepository;
import me.acharliekelly.canvas.description.DescriptionRepository;
import me.acharliekelly.canvas.publication.PublicationRepository;
import me.acharliekelly.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicAssetApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired GeneratedAssetRepository assetRepository;
    @Autowired PublicationRepository publicationRepository;
    @Autowired DescriptionRepository descriptionRepository;
    @Autowired ArtworkRepository artworkRepository;

    @MockitoBean(name = "originalObjectStorage") ObjectStorage originalStorage;
    @MockitoBean(name = "generatedObjectStorage") ObjectStorage generatedStorage;

    private final Map<String, byte[]> objects = new HashMap<>();
    private final Map<String, ObjectStorage.ObjectMetadata> metadata = new HashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    @BeforeEach
    void cleanDatabaseAndStorage() throws Exception {
        publicationRepository.deleteAll();
        assetRepository.deleteAll();
        descriptionRepository.deleteAll();
        artworkRepository.deleteAll();
        objects.clear();
        metadata.clear();
        sequence.set(0);
        when(originalStorage.put(any(), anyLong(), anyString())).thenAnswer(invocation -> {
            String key = "objects/" + sequence.incrementAndGet();
            objects.put(key, invocation.<java.io.InputStream>getArgument(0).readAllBytes());
            return new ObjectStorage.StoredObject(key);
        });
        when(generatedStorage.putGenerated(anyString(), any(), anyLong(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            objects.put(key, invocation.<java.io.InputStream>getArgument(1).readAllBytes());
            metadata.put(key, new ObjectStorage.ObjectMetadata(
                    invocation.getArgument(2), invocation.getArgument(3)));
            return new ObjectStorage.StoredObject(key);
        });
        when(originalStorage.get(anyString())).thenAnswer(invocation -> objectContent(invocation.getArgument(0)));
        when(generatedStorage.get(anyString())).thenAnswer(invocation -> objectContent(invocation.getArgument(0)));
        when(generatedStorage.head(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(metadata.get(invocation.getArgument(0))));
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            objects.remove(key);
            metadata.remove(key);
            return null;
        }).when(generatedStorage).delete(anyString());
    }

    @Test
    void publishedAudioAndQrHaveExactPublicCachingAndDownloadHeaders() throws Exception {
        JsonNode artwork = uploadArtwork("Blue Study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        approveDescription(artworkId, createDescription(artworkId, "Objective", "A blue square."));
        JsonNode publication = publishResult(artworkId);
        String slug = publication.get("slug").asText();
        GeneratedAsset audioAsset = assetRepository.findAll().stream()
                .filter(asset -> asset.getKind() == AssetKind.AUDIO).findFirst().orElseThrow();
        GeneratedAsset qrAsset = assetRepository.findAll().stream()
                .filter(asset -> asset.getKind() == AssetKind.QR_CODE).findFirst().orElseThrow();

        String publicJson = mvc.perform(get("/public/artworks/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descriptions[0].text").value("A blue square."))
                .andExpect(jsonPath("$.descriptions[0].audioUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String audioUrl = json.readTree(publicJson).get("descriptions").get(0).get("audioUrl").asText();
        org.assertj.core.api.Assertions.assertThat(audioUrl).isEqualTo(
                "/public/artworks/" + slug + "/descriptions/"
                        + currentDescriptionId(slug) + "/audio/" + audioAsset.getId());
        org.assertj.core.api.Assertions.assertThat(publication.get("qrUrl").asText()).isEqualTo(
                "/public/artworks/" + slug + "/qr/" + qrAsset.getId());

        byte[] audio = mvc.perform(get(audioUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/wav"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(audio)
                .startsWith(new byte[] { 'R', 'I', 'F', 'F' });

        byte[] qr = mvc.perform(get(publication.get("qrUrl").asText()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        "public, max-age=31536000, immutable"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + slug + "-qr.png\""))
                .andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(QrTestDecoder.decode(qr))
                .isEqualTo("http://localhost:5173/artworks/" + slug);
    }

    @Test
    void generatedAssetsAreInaccessibleWithoutACurrentPublicationOrFromAnOldSnapshot() throws Exception {
        mvc.perform(get("/public/artworks/not-published/qr/{assetId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/public/artworks/not-published/descriptions/{id}/audio/{assetId}",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isNotFound());

        JsonNode artwork = uploadArtwork("Blue Study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        JsonNode description = approveDescription(artworkId,
                createDescription(artworkId, "Objective", "Original approved text."));
        String slug = publish(artworkId);
        String firstAudioUrl = audioUrl(slug);

        JsonNode draft = updateDraft(artworkId, description, "Replacement approved text.");
        approveDescription(artworkId, draft);
        publish(artworkId);

        mvc.perform(get(firstAudioUrl)).andExpect(status().isNotFound());
        mvc.perform(get(audioUrl(slug))).andExpect(status().isOk());
    }

    @Test
    void missingPublishedAssetObjectReturnsSafeServiceUnavailableProblem() throws Exception {
        JsonNode artwork = uploadArtwork("Blue Study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        approveDescription(artworkId, createDescription(artworkId, "Objective", "A blue square."));
        String slug = publish(artworkId);
        String audioUrl = audioUrl(slug);
        GeneratedAsset audio = assetRepository.findAll().stream()
                .filter(asset -> asset.getKind() == AssetKind.AUDIO).findFirst().orElseThrow();
        objects.remove(audio.getObjectKey());
        metadata.remove(audio.getObjectKey());

        mvc.perform(get(audioUrl))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("public_asset_unavailable"))
                .andExpect(jsonPath("$.detail").value("The published asset is temporarily unavailable."));
    }

    private ByteArrayInputStream objectContent(String key) {
        byte[] content = objects.get(key);
        if (content == null) {
            throw new IllegalStateException("Object is missing.");
        }
        return new ByteArrayInputStream(content);
    }

    private String audioUrl(String slug) throws Exception {
        String body = mvc.perform(get("/public/artworks/{slug}", slug))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("descriptions").get(0).get("audioUrl").asText();
    }

    private String publish(UUID artworkId) throws Exception {
        return publishResult(artworkId).get("slug").asText();
    }

    private JsonNode publishResult(UUID artworkId) throws Exception {
        String response = mvc.perform(post("/api/artworks/{artworkId}/publication", artworkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + artworkVersion(artworkId) + "}")
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private UUID currentDescriptionId(String slug) {
        return publicationRepository.findCurrentBySlug(slug).orElseThrow()
                .getDescriptions().getFirst().getId();
    }

    private JsonNode uploadArtwork(String title) throws Exception {
        String response = mvc.perform(multipart("/api/artworks")
                        .file(validPng()).param("title", title).param("credit", "A. Artist")
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

    private JsonNode approveDescription(UUID artworkId, JsonNode description) throws Exception {
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

    private long artworkVersion(UUID artworkId) throws Exception {
        String response = mvc.perform(get("/api/artworks/{artworkId}", artworkId).with(user("admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("version").asLong();
    }

    private static MockMultipartFile validPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("image", "art.png", "image/png", bytes.toByteArray());
    }
}
