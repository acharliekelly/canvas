package org.canvas.artwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "canvas.admin.username=admin",
        "canvas.admin.password-hash={noop}password",
        "canvas.upload-max-size=1MB"
})
class ArtworkUploadIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ArtworkRepository repository;

    @MockitoBean
    ObjectStorage storage;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
        when(storage.put(any(), anyLong(), anyString()))
                .thenAnswer(invocation -> new ObjectStorage.StoredObject("originals/" + UUID.randomUUID()));
    }

    @Test
    void validPngAndJpegUploadsPersistArtworkRows() throws Exception {
        upload(ArtworkApiTest.validPng(), "Blue Study");
        upload(ArtworkApiTest.validJpeg(), "Red Study");

        assertThat(repository.findAll()).extracting(Artwork::getTitle)
                .containsExactlyInAnyOrder("Blue Study", "Red Study");
    }

    @Test
    void storageFailureLeavesNoArtworkRow() throws Exception {
        doThrow(new IllegalStateException("database unavailable")).when(storage).put(any(), anyLong(), anyString());

        uploadExpectingFailure(ArtworkApiTest.validPng(), "Blue Study");

        assertThat(repository.count()).isZero();
    }

    private void upload(org.springframework.mock.web.MockMultipartFile image, String title) throws Exception {
        mvc.perform(multipart("/api/artworks")
                        .file(image)
                        .param("title", title)
                        .param("credit", "A. Artist")
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    private void uploadExpectingFailure(org.springframework.mock.web.MockMultipartFile image, String title) throws Exception {
        mvc.perform(multipart("/api/artworks")
                        .file(image)
                        .param("title", title)
                        .param("credit", "A. Artist")
                        .with(user("admin"))
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable());
    }
}
