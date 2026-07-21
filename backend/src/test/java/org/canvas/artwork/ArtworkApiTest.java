package org.canvas.artwork;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import jakarta.servlet.MultipartConfigElement;
import org.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "canvas.admin.username=admin",
        "canvas.admin.password-hash={noop}password",
        "canvas.upload-max-size=1KB"
})
class ArtworkApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ArtworkRepository repository;

    @Autowired
    MultipartConfigElement multipartConfig;

    @MockitoBean
    ObjectStorage storage;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
        when(storage.put(any(), anyLong(), anyString()))
                .thenReturn(new ObjectStorage.StoredObject("originals/test-object"));
    }

    @Test
    void servletUsesTheConfiguredUploadLimit() {
        org.assertj.core.api.Assertions.assertThat(multipartConfig.getMaxFileSize()).isEqualTo(1024);
        org.assertj.core.api.Assertions.assertThat(multipartConfig.getMaxRequestSize()).isGreaterThan(1024);
    }

    @Test
    void unauthenticatedArtworkRequestsReturnJsonUnauthorized() throws Exception {
        mvc.perform(get("/api/artworks"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void csrfIsRequiredForUpload() throws Exception {
        mvc.perform(multipart("/api/artworks")
                        .file(validPng())
                        .param("title", "Blue Study")
                        .param("credit", "A. Artist")
                        .with(user("admin")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void publicationDataIsNotExposedFromAnUpload() throws Exception {
        mvc.perform(multipart("/api/artworks")
                        .file(validPng())
                        .param("title", "Blue Study")
                        .param("credit", "A. Artist")
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Blue Study"))
                .andExpect(jsonPath("$.imageObjectKey").doesNotExist())
                .andExpect(jsonPath("$.publicSlug").doesNotExist());
    }

    @Test
    void listAndDetailNeverExposeObjectKey() throws Exception {
        String body = mvc.perform(multipart("/api/artworks")
                        .file(validJpeg())
                        .param("title", "Red Study")
                        .param("credit", "B. Artist")
                        .param("context", "Gallery note")
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String artworkId = body.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mvc.perform(get("/api/artworks").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Red Study"))
                .andExpect(jsonPath("$[0].imageObjectKey").doesNotExist());
        mvc.perform(get("/api/artworks/{id}", artworkId).with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context").value("Gallery note"))
                .andExpect(jsonPath("$.imageObjectKey").doesNotExist());
    }

    @Test
    void rejectsUnsupportedDeclaredTypeAsProblemDetails() throws Exception {
        assertRejected(new MockMultipartFile("image", "art.gif", "image/gif", validImage("gif")),
                "unsupported_media_type", "image");
    }

    @Test
    void rejectsOversizedImageAsProblemDetails() throws Exception {
        assertRejected(new MockMultipartFile("image", "large.png", "image/png", new byte[1025]),
                "image_too_large", "image");
    }

    @Test
    void rejectsUndecodableImageAsProblemDetails() throws Exception {
        assertRejected(new MockMultipartFile("image", "broken.png", "image/png", "not an image".getBytes()),
                "invalid_image", "image");
    }

    @Test
    void rejectsOverlongTitleBeforeStoringTheImage() throws Exception {
        mvc.perform(multipart("/api/artworks")
                        .file(validPng())
                        .param("title", "T".repeat(256))
                        .param("credit", "A. Artist")
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("title_too_long"))
                .andExpect(jsonPath("$.field").value("title"));

        verify(storage, never()).put(any(), anyLong(), anyString());
    }

    @Test
    void rejectsOverlongCreditBeforeStoringTheImage() throws Exception {
        mvc.perform(multipart("/api/artworks")
                        .file(validPng())
                        .param("title", "Blue Study")
                        .param("credit", "A".repeat(256))
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("credit_too_long"))
                .andExpect(jsonPath("$.field").value("credit"));

        verify(storage, never()).put(any(), anyLong(), anyString());
    }

    @Test
    void identifiesTheMissingMetadataField() throws Exception {
        mvc.perform(multipart("/api/artworks")
                        .file(validPng())
                        .param("credit", "A. Artist")
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"))
                .andExpect(jsonPath("$.field").value("title"));
    }

    @Test
    void storageFailureLeavesNoArtworkRow() throws Exception {
        doThrow(new IllegalStateException("storage unavailable"))
                .when(storage).put(any(), anyLong(), anyString());

        mvc.perform(multipart("/api/artworks")
                        .file(validPng())
                        .param("title", "Blue Study")
                        .param("credit", "A. Artist")
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("storage_unavailable"));

        org.assertj.core.api.Assertions.assertThat(repository.count()).isZero();
    }

    private void assertRejected(MockMultipartFile file, String code, String field) throws Exception {
        mvc.perform(multipart("/api/artworks")
                        .file(file)
                        .param("title", "Blue Study")
                        .param("credit", "A. Artist")
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.field").value(field));
    }

    static MockMultipartFile validPng() throws Exception {
        return new MockMultipartFile("image", "art.png", "image/png", validImage("png"));
    }

    static MockMultipartFile validJpeg() throws Exception {
        return new MockMultipartFile("image", "art.jpg", "image/jpeg", validImage("jpeg"));
    }

    private static byte[] validImage(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return bytes.toByteArray();
    }
}
