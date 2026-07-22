package org.canvas.caption;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.canvas.description.DescriptionRepository;
import org.canvas.description.DescriptionService;
import org.canvas.description.DescriptionSource;
import org.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "canvas.caption-auto-submit=false")
@ActiveProfiles("test")
class CaptionJobServiceTest {
    @Autowired CaptionJobService service;
    @Autowired CaptionJobRunner runner;
    @Autowired CaptionJobRepository jobs;
    @Autowired DescriptionService descriptions;
    @Autowired DescriptionRepository descriptionRepository;
    @Autowired ArtworkService artworkService;
    @Autowired ArtworkRepository artworkRepository;

    @MockitoBean CaptionClient client;
    @MockitoBean ObjectStorage storage;

    @BeforeEach
    void cleanDatabase() {
        jobs.deleteAll();
        descriptionRepository.deleteAll();
        artworkRepository.deleteAll();
        when(storage.put(any(), anyLong(), anyString()))
                .thenAnswer(invocation -> new ObjectStorage.StoredObject("originals/" + UUID.randomUUID()));
    }

    @Test
    void pendingJobRunsToSuccessAndCreatesExactlyOneGeneratedDraft() throws Exception {
        ArtworkDetail artwork = createArtwork("Blue Study");
        var manual = descriptions.createManual(artwork.id(), "Objective", "A manually written description.");
        when(client.caption(any())).thenAnswer(invocation -> {
            assertThat(service.get(artwork.id(), jobs.findAll().getFirst().getId()).state())
                    .isEqualTo(CaptionJob.State.RUNNING);
            return new CaptionClient.CaptionResponse(
                    "Placeholder draft", "Metadata-only demo text.", "deterministic-placeholder", "1");
        });

        var pending = service.request(artwork.id());
        assertThat(pending.state()).isEqualTo(CaptionJob.State.PENDING);

        runner.run(pending.jobId());

        var succeeded = service.get(artwork.id(), pending.jobId());
        assertThat(succeeded.state()).isEqualTo(CaptionJob.State.SUCCEEDED);
        assertThat(succeeded.resultingDescriptionId()).isNotNull();
        assertThat(descriptions.listForArtwork(artwork.id()))
                .filteredOn(item -> item.source() == DescriptionSource.GENERATED)
                .singleElement()
                .satisfies(item -> assertThat(item.descriptionId()).isEqualTo(succeeded.resultingDescriptionId()));
        assertThat(descriptions.listForArtwork(artwork.id()))
                .filteredOn(item -> item.source() == DescriptionSource.MANUAL)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.descriptionId()).isEqualTo(manual.descriptionId());
                    assertThat(item.currentRevision().text()).isEqualTo("A manually written description.");
                });

        runner.run(pending.jobId());
        assertThat(descriptions.listForArtwork(artwork.id()))
                .filteredOn(item -> item.source() == DescriptionSource.GENERATED)
                .hasSize(1);
    }

    @Test
    void workerFailureIsSafeAndRetryCreatesOneNewAttempt() throws Exception {
        ArtworkDetail artwork = createArtwork("Retry Study");
        when(client.caption(any())).thenThrow(new IllegalStateException("secret worker endpoint failed"));

        var first = service.request(artwork.id());
        assertThat(service.request(artwork.id()).jobId()).isEqualTo(first.jobId());
        runner.run(first.jobId());

        var failed = service.get(artwork.id(), first.jobId());
        assertThat(failed.state()).isEqualTo(CaptionJob.State.FAILED);
        assertThat(failed.errorMessage()).doesNotContain("secret");

        var retry = service.request(artwork.id());
        assertThat(retry.jobId()).isNotEqualTo(first.jobId());
        assertThat(retry.attemptCount()).isEqualTo(2);
        assertThat(service.request(artwork.id()).jobId()).isEqualTo(retry.jobId());
    }

    private ArtworkDetail createArtwork(String title) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return artworkService.upload(new MockMultipartFile("image", "art.png", "image/png", bytes.toByteArray()),
                title, "A. Artist", null);
    }
}
