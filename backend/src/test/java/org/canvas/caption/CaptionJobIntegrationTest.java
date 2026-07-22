package org.canvas.caption;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.canvas.artwork.ArtworkRepository;
import org.canvas.description.DescriptionRepository;
import org.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
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

@SpringBootTest(properties = "canvas.caption-auto-submit=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaptionJobIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CaptionJobRunner runner;
    @Autowired CaptionJobRepository jobs;
    @Autowired DescriptionRepository descriptions;
    @Autowired ArtworkRepository artworks;

    @MockitoBean CaptionClient client;
    @MockitoBean ObjectStorage storage;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        when(storage.put(any(), anyLong(), anyString()))
                .thenAnswer(invocation -> new ObjectStorage.StoredObject("originals/" + UUID.randomUUID()));
        when(client.caption(any())).thenReturn(new CaptionClient.CaptionResponse(
                "Placeholder draft", "Metadata-only demo text.", "deterministic-placeholder", "1"));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void captionEndpointsRequireAuthenticationAndKeepArtworkOwnership() throws Exception {
        UUID firstArtwork = uploadArtwork("First");
        UUID secondArtwork = uploadArtwork("Second");

        mvc.perform(post("/api/artworks/{artworkId}/caption-jobs", firstArtwork).with(csrf()))
                .andExpect(status().isUnauthorized());

        JsonNode job = request(firstArtwork);
        mvc.perform(get("/api/artworks/{artworkId}/caption-jobs/{jobId}", secondArtwork,
                        job.get("jobId").asText()).with(user("admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("caption_job_not_found"));
    }

    @Test
    void createsPollableAcceptedJobAndReturnsGeneratedDescriptionId() throws Exception {
        UUID artworkId = uploadArtwork("Blue Study");

        JsonNode pending = request(artworkId);
        UUID jobId = UUID.fromString(pending.get("jobId").asText());
        runner.run(jobId);

        mvc.perform(get("/api/artworks/{artworkId}/caption-jobs/{jobId}", artworkId, jobId)
                        .with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultingDescriptionId").isNotEmpty());
    }

    private JsonNode request(UUID artworkId) throws Exception {
        String response = mvc.perform(post("/api/artworks/{artworkId}/caption-jobs", artworkId)
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/caption-jobs/")))
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private UUID uploadArtwork(String title) throws Exception {
        String response = mvc.perform(multipart("/api/artworks")
                        .file(validPng()).param("title", title).param("credit", "A. Artist")
                        .with(user("admin")).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private static MockMultipartFile validPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("image", "art.png", MediaType.IMAGE_PNG_VALUE, bytes.toByteArray());
    }

    private void cleanDatabase() {
        jobs.deleteAll();
        descriptions.deleteAll();
        artworks.deleteAll();
    }
}
