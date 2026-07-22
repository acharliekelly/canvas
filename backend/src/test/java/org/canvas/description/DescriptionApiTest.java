package org.canvas.description;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.canvas.artwork.ArtworkRepository;
import org.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class DescriptionApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    DescriptionRepository descriptionRepository;

    @Autowired
    ArtworkRepository artworkRepository;

    @MockitoBean(name = "originalObjectStorage")
    ObjectStorage storage;

    @BeforeEach
    void cleanDatabase() {
        descriptionRepository.deleteAll();
        artworkRepository.deleteAll();
        when(storage.put(any(), anyLong(), anyString()))
                .thenAnswer(invocation -> new ObjectStorage.StoredObject("originals/" + UUID.randomUUID()));
    }

    @Test
    void descriptionEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/artworks/{artworkId}/descriptions", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void artworkInitiallyReturnsZeroDescriptions() throws Exception {
        UUID artworkId = UUID.fromString(uploadArtwork("Empty").get("id").asText());

        mvc.perform(get("/api/artworks/{artworkId}/descriptions", artworkId).with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createsListsEditsAndApprovesDescriptionsWithAuditHistory() throws Exception {
        JsonNode artwork = uploadArtwork("Blue Study");
        UUID artworkId = UUID.fromString(artwork.get("id").asText());
        JsonNode objective = createDescription(artworkId, "Objective", "A blue square.");
        JsonNode subjective = createDescription(artworkId, "Subjective", "It feels expansive.");

        mvc.perform(get("/api/artworks/{artworkId}/descriptions", artworkId).with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentRevision.label").value("Objective"))
                .andExpect(jsonPath("$[1].currentRevision.label").value("Subjective"));

        UUID objectiveId = UUID.fromString(objective.get("descriptionId").asText());
        long objectiveVersion = objective.get("version").asLong();
        String approveBody = "{\"version\":" + objectiveVersion + "}";
        String approvedJson = mvc.perform(post(
                        "/api/artworks/{artworkId}/descriptions/{descriptionId}/approve", artworkId, objectiveId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBody)
                        .with(user("configured-admin"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRevision.state").value("APPROVED"))
                .andExpect(jsonPath("$.currentRevision.approvedBy").value("configured-admin"))
                .andExpect(jsonPath("$.currentRevision.approvedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode approved = json.readTree(approvedJson);

        mvc.perform(put("/api/artworks/{artworkId}/descriptions/{descriptionId}/draft", artworkId, objectiveId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new DraftRequest("Objective", "A cobalt square.",
                                approved.get("version").asLong())))
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRevision.state").value("DRAFT"))
                .andExpect(jsonPath("$.revisions.length()").value(2))
                .andExpect(jsonPath("$.revisions[0].text").value("A blue square."))
                .andExpect(jsonPath("$.revisions[0].state").value("APPROVED"))
                .andExpect(jsonPath("$.revisions[1].text").value("A cobalt square."));

        long artworkVersion = artworkVersion(artworkId);
        mvc.perform(put("/api/artworks/{artworkId}/description-order", artworkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descriptionIds\":[\"" + subjective.get("descriptionId").asText()
                                + "\",\"" + objectiveId + "\"],\"version\":" + artworkVersion + "}")
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].descriptionId").value(subjective.get("descriptionId").asText()))
                .andExpect(jsonPath("$[1].descriptionId").value(objectiveId.toString()));
    }

    @Test
    void rejectsBlankFieldsWrongOwnershipIncompleteOrderingAndStaleVersions() throws Exception {
        JsonNode firstArtwork = uploadArtwork("First");
        JsonNode secondArtwork = uploadArtwork("Second");
        UUID firstId = UUID.fromString(firstArtwork.get("id").asText());
        UUID secondId = UUID.fromString(secondArtwork.get("id").asText());

        mvc.perform(post("/api/artworks/{artworkId}/descriptions", firstId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\" \",\"text\":\"Text\"}")
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_description"))
                .andExpect(jsonPath("$.field").value("label"));

        JsonNode description = createDescription(firstId, "Objective", "Text.");
        UUID descriptionId = UUID.fromString(description.get("descriptionId").asText());
        long version = description.get("version").asLong();

        mvc.perform(put("/api/artworks/{artworkId}/descriptions/{descriptionId}/draft", secondId, descriptionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new DraftRequest("Objective", "Changed.", version)))
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("description_not_found"));

        mvc.perform(put("/api/artworks/{artworkId}/description-order", firstId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descriptionIds\":[],\"version\":" + artworkVersion(firstId) + "}")
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_description_order"));

        mvc.perform(post("/api/artworks/{artworkId}/descriptions/{descriptionId}/approve", firstId, descriptionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + (version + 10) + "}")
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale_version"));
    }

    private JsonNode createDescription(UUID artworkId, String label, String text) throws Exception {
        String response = mvc.perform(post("/api/artworks/{artworkId}/descriptions", artworkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new CreateRequest(label, text)))
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private JsonNode uploadArtwork(String title) throws Exception {
        String response = mvc.perform(multipart("/api/artworks")
                        .file(validPng())
                        .param("title", title)
                        .param("credit", "A. Artist")
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private long artworkVersion(UUID artworkId) throws Exception {
        String response = mvc.perform(get("/api/artworks/{artworkId}", artworkId)
                        .with(user("admin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("version").asLong();
    }

    private static MockMultipartFile validPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("image", "art.png", "image/png", bytes.toByteArray());
    }

    private record CreateRequest(String label, String text) {}
    private record DraftRequest(String label, String text, long version) {}
}
