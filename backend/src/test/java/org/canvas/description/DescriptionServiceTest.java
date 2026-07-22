package org.canvas.description;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.canvas.artwork.ArtworkRepository;
import org.canvas.artwork.ArtworkService;
import org.canvas.artwork.api.ArtworkDetail;
import org.canvas.description.DescriptionService.DescriptionProblem;
import org.canvas.description.api.DescriptionResponse;
import org.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class DescriptionServiceTest {
    @Autowired
    DescriptionService service;

    @Autowired
    DescriptionRepository repository;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    ArtworkRepository artworkRepository;

    @MockitoBean
    ObjectStorage storage;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
        artworkRepository.deleteAll();
        when(storage.put(any(), anyLong(), anyString()))
                .thenAnswer(invocation -> new ObjectStorage.StoredObject("originals/" + UUID.randomUUID()));
    }

    @Test
    void artworkCanHaveZeroDescriptions() throws Exception {
        UUID artworkId = createArtwork("Empty").id();

        assertThat(service.listForArtwork(artworkId)).isEmpty();
    }

    @Test
    void manualDescriptionsKeepFreeFormLabelsAndStableOrder() throws Exception {
        ArtworkDetail artwork = createArtwork("Two descriptions");

        DescriptionResponse objective = service.createManual(artwork.id(), "Objective", "A blue square.");
        DescriptionResponse subjective = service.createManual(artwork.id(), "Subjective", "It feels expansive.");

        assertThat(service.listForArtwork(artwork.id()))
                .extracting(response -> response.currentRevision().label())
                .containsExactly("Objective", "Subjective");
        assertThat(objective.displayOrder()).isZero();
        assertThat(subjective.displayOrder()).isEqualTo(1);
        assertThat(objective.source()).isEqualTo(DescriptionSource.MANUAL);
    }

    @Test
    void generatedDraftUsesTheSharedRevisionWorkflow() throws Exception {
        ArtworkDetail artwork = createArtwork("Generated");

        DescriptionResponse generated = service.createGeneratedDraft(
                artwork.id(), "Model draft", "A generated draft description.");

        assertThat(generated.source()).isEqualTo(DescriptionSource.GENERATED);
        assertThat(generated.currentRevision().state()).isEqualTo(RevisionState.DRAFT);
    }

    @Test
    void labelsAndTextAreRequired() throws Exception {
        UUID artworkId = createArtwork("Validation").id();

        assertThatThrownBy(() -> service.createManual(artworkId, " ", "Text"))
                .isInstanceOf(DescriptionProblem.class)
                .extracting("code", "field")
                .containsExactly("invalid_description", "label");
        assertThatThrownBy(() -> service.createManual(artworkId, "Objective", " "))
                .isInstanceOf(DescriptionProblem.class)
                .extracting("code", "field")
                .containsExactly("invalid_description", "text");
    }

    @Test
    void mutationsEnforceArtworkOwnership() throws Exception {
        ArtworkDetail first = createArtwork("First");
        ArtworkDetail second = createArtwork("Second");
        DescriptionResponse description = service.createManual(first.id(), "Objective", "First text.");

        assertThatThrownBy(() -> service.updateDraft(second.id(), description.descriptionId(),
                "Objective", "Changed.", description.version()))
                .isInstanceOf(DescriptionProblem.class)
                .extracting("code")
                .isEqualTo("description_not_found");
        assertThatThrownBy(() -> service.approve(second.id(), description.descriptionId(),
                "admin", description.version()))
                .isInstanceOf(DescriptionProblem.class)
                .extracting("code")
                .isEqualTo("description_not_found");
    }

    @Test
    void staleDescriptionVersionCannotOverwriteNewerDraft() throws Exception {
        ArtworkDetail artwork = createArtwork("Concurrency");
        DescriptionResponse created = service.createManual(artwork.id(), "Objective", "First text.");
        DescriptionResponse saved = service.updateDraft(artwork.id(), created.descriptionId(),
                "Objective", "Newer text.", created.version());

        assertThat(saved.version()).isGreaterThan(created.version());
        assertThatThrownBy(() -> service.updateDraft(artwork.id(), created.descriptionId(),
                "Objective", "Stale text.", created.version()))
                .isInstanceOf(DescriptionProblem.class)
                .extracting("code")
                .isEqualTo("stale_version");
    }

    @Test
    void approvalRecordsApproverAndTimestamp() throws Exception {
        ArtworkDetail artwork = createArtwork("Approval");
        DescriptionResponse created = service.createManual(artwork.id(), "Objective", "A blue square.");
        Instant before = Instant.now();

        DescriptionResponse approved = service.approve(
                artwork.id(), created.descriptionId(), "configured-admin", created.version());

        assertThat(approved.currentRevision().state()).isEqualTo(RevisionState.APPROVED);
        assertThat(approved.currentRevision().approvedBy()).isEqualTo("configured-admin");
        assertThat(approved.currentRevision().approvedAt()).isAfterOrEqualTo(before);
        assertThat(approved.approvedRevisionId()).isEqualTo(approved.currentRevision().revisionId());
    }

    @Test
    void editingApprovedDescriptionCreatesDraftRevision() throws Exception {
        ArtworkDetail artwork = createArtwork("Revision history");
        DescriptionResponse created = service.createManual(artwork.id(), "Objective", "A blue square.");
        DescriptionResponse approved = service.approve(
                artwork.id(), created.descriptionId(), "admin", created.version());

        DescriptionResponse edited = service.updateDraft(artwork.id(), approved.descriptionId(),
                "Objective", "A cobalt square.", approved.version());

        assertThat(edited.currentRevision().state()).isEqualTo(RevisionState.DRAFT);
        assertThat(edited.currentRevision().parentRevisionId()).isEqualTo(approved.approvedRevisionId());
        assertThat(repository.findRevision(approved.approvedRevisionId()).getText()).isEqualTo("A blue square.");
        assertThat(edited.revisions())
                .extracting(DescriptionResponse.RevisionResponse::text)
                .containsExactly("A blue square.", "A cobalt square.");
    }

    @Test
    void reorderRequiresExactCurrentSetAndArtworkVersion() throws Exception {
        ArtworkDetail artwork = createArtwork("Ordering");
        DescriptionResponse objective = service.createManual(artwork.id(), "Objective", "First.");
        DescriptionResponse subjective = service.createManual(artwork.id(), "Subjective", "Second.");

        long currentArtworkVersion = artworkRepository.findById(artwork.id()).orElseThrow().getVersion();
        assertThatThrownBy(() -> service.reorder(
                artwork.id(), List.of(objective.descriptionId()), currentArtworkVersion))
                .isInstanceOf(DescriptionProblem.class)
                .extracting("code")
                .isEqualTo("invalid_description_order");

        List<DescriptionResponse> reordered = service.reorder(artwork.id(),
                List.of(subjective.descriptionId(), objective.descriptionId()), currentArtworkVersion);
        assertThat(reordered).extracting(DescriptionResponse::descriptionId)
                .containsExactly(subjective.descriptionId(), objective.descriptionId());
        assertThatThrownBy(() -> service.reorder(artwork.id(),
                List.of(objective.descriptionId(), subjective.descriptionId()), artwork.version()))
                .isInstanceOf(DescriptionProblem.class)
                .extracting("code")
                .isEqualTo("stale_version");
    }

    private ArtworkDetail createArtwork(String title) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return artworkService.upload(new MockMultipartFile("image", "art.png", "image/png", bytes.toByteArray()),
                title, "A. Artist", null);
    }
}
