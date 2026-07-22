package org.canvas.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.canvas.artwork.ArtworkRepository;
import org.canvas.artwork.ArtworkService;
import org.canvas.artwork.api.ArtworkDetail;
import org.canvas.description.DescriptionRepository;
import org.canvas.description.DescriptionService;
import org.canvas.description.api.DescriptionResponse;
import org.canvas.publication.PublicationService.PublicationNotAllowed;
import org.canvas.publication.PublicationService.PublicationProblem;
import org.canvas.publication.PublicationService.PublicationResult;
import org.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class PublicationServiceTest {
    private static final UUID ADMIN_ID = UUID.fromString("1945c6e9-d034-45a7-b10a-024cad3fc685");

    @Autowired
    PublicationService service;

    @Autowired
    PublicationRepository publicationRepository;

    @Autowired
    DescriptionService descriptionService;

    @Autowired
    DescriptionRepository descriptionRepository;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    ArtworkRepository artworkRepository;

    @MockitoBean
    ObjectStorage storage;

    @BeforeEach
    void cleanDatabase() {
        publicationRepository.deleteAll();
        descriptionRepository.deleteAll();
        artworkRepository.deleteAll();
        when(storage.put(any(), anyLong(), anyString()))
                .thenAnswer(invocation -> new ObjectStorage.StoredObject("originals/" + UUID.randomUUID()));
    }

    @AfterEach
    void removePublicationSnapshots() {
        publicationRepository.deleteAll();
    }

    @Test
    void requiresAtLeastOneApprovedDescription() throws Exception {
        ArtworkDetail artwork = createArtwork("Draft only");
        descriptionService.createManual(artwork.id(), "Objective", "A draft description.");

        assertThatThrownBy(() -> service.publish(artwork.id(), currentVersion(artwork.id()), ADMIN_ID))
                .isInstanceOf(PublicationNotAllowed.class)
                .hasMessageContaining("Approve at least one description");
        assertThat(publicationRepository.count()).isZero();
    }

    @Test
    void snapshotsOnlyLatestApprovedRevisionsInDisplayOrder() throws Exception {
        ArtworkDetail artwork = createArtwork("Blue Study");
        DescriptionResponse objective = approve(artwork.id(),
                descriptionService.createManual(artwork.id(), "Objective", "A blue square."));
        DescriptionResponse subjective = approve(artwork.id(),
                descriptionService.createManual(artwork.id(), "Subjective", "The square feels expansive."));
        descriptionService.createManual(artwork.id(), "Editorial draft", "This must stay private.");
        descriptionService.reorder(artwork.id(),
                List.of(subjective.descriptionId(), objective.descriptionId(),
                        descriptionService.listForArtwork(artwork.id()).get(2).descriptionId()),
                currentVersion(artwork.id()));

        PublicationResult result = service.publish(artwork.id(), currentVersion(artwork.id()), ADMIN_ID);

        assertThat(result.slug()).matches("blue-study-[0-9a-f-]{36}");
        assertThat(result.created()).isTrue();
        assertThat(result.descriptions())
                .extracting(PublicationService.PublishedDescriptionResult::label)
                .containsExactly("Subjective", "Objective");
        assertThat(result.descriptions())
                .extracting(PublicationService.PublishedDescriptionResult::text)
                .containsExactly("The square feels expansive.", "A blue square.");
        assertThat(result.descriptions()).noneMatch(item -> item.text().contains("private"));
    }

    @Test
    void repeatedPublicationIsIdempotentByContentAndKeepsTheSlug() throws Exception {
        ArtworkDetail artwork = createArtwork("Blue Study");
        approve(artwork.id(), descriptionService.createManual(artwork.id(), "Objective", "A blue square."));

        PublicationResult first = service.publish(artwork.id(), currentVersion(artwork.id()), ADMIN_ID);
        PublicationResult repeated = service.publish(artwork.id(), first.artworkVersion(), ADMIN_ID);

        assertThat(repeated.created()).isFalse();
        assertThat(repeated.slug()).isEqualTo(first.slug());
        assertThat(repeated.publishedAt()).isEqualTo(first.publishedAt());
        assertThat(repeated.artworkVersion()).isEqualTo(first.artworkVersion());
        assertThat(publicationRepository.countByArtworkId(artwork.id())).isOne();
    }

    @Test
    void editingAnApprovedDescriptionDoesNotPublishTheDraftOrCreateAnotherSnapshot() throws Exception {
        ArtworkDetail artwork = createArtwork("Blue Study");
        DescriptionResponse approved = approve(artwork.id(),
                descriptionService.createManual(artwork.id(), "Objective", "A blue square."));
        PublicationResult first = service.publish(artwork.id(), currentVersion(artwork.id()), ADMIN_ID);

        descriptionService.updateDraft(artwork.id(), approved.descriptionId(), "Objective",
                "A private cobalt draft.", approved.version());
        PublicationResult repeated = service.publish(artwork.id(), currentVersion(artwork.id()), ADMIN_ID);

        assertThat(repeated.created()).isFalse();
        assertThat(repeated.publishedAt()).isEqualTo(first.publishedAt());
        assertThat(repeated.descriptions()).singleElement()
                .extracting(PublicationService.PublishedDescriptionResult::text)
                .isEqualTo("A blue square.");
        assertThat(publicationRepository.countByArtworkId(artwork.id())).isOne();
    }

    @Test
    void aNewApprovalCreatesANewCurrentSnapshotWithoutChangingTheSlug() throws Exception {
        ArtworkDetail artwork = createArtwork("Blue Study");
        DescriptionResponse approved = approve(artwork.id(),
                descriptionService.createManual(artwork.id(), "Objective", "A blue square."));
        PublicationResult first = service.publish(artwork.id(), currentVersion(artwork.id()), ADMIN_ID);
        DescriptionResponse draft = descriptionService.updateDraft(artwork.id(), approved.descriptionId(),
                "Objective", "A cobalt square.", approved.version());
        approve(artwork.id(), draft);

        PublicationResult second = service.publish(artwork.id(), currentVersion(artwork.id()), ADMIN_ID);

        assertThat(second.created()).isTrue();
        assertThat(second.slug()).isEqualTo(first.slug());
        assertThat(second.descriptions()).singleElement()
                .extracting(PublicationService.PublishedDescriptionResult::text)
                .isEqualTo("A cobalt square.");
        assertThat(publicationRepository.countByArtworkId(artwork.id())).isEqualTo(2);
        assertThat(publicationRepository.findCurrentByArtworkId(artwork.id())).get()
                .extracting(Publication::getId).isEqualTo(second.publicationId());
    }

    @Test
    void approvalAfterPreviewMakesThePreviewVersionStale() throws Exception {
        ArtworkDetail artwork = createArtwork("Stale publication preview");
        DescriptionResponse draft = descriptionService.createManual(
                artwork.id(), "Objective", "A blue square.");
        long previewVersion = currentVersion(artwork.id());

        approve(artwork.id(), draft);

        assertThatThrownBy(() -> service.publish(artwork.id(), previewVersion, ADMIN_ID))
                .isInstanceOf(PublicationProblem.class)
                .extracting("code")
                .isEqualTo("stale_version");
        assertThat(publicationRepository.countByArtworkId(artwork.id())).isZero();
    }

    @Test
    void sameTextReapprovalCreatesANewAuditSnapshot() throws Exception {
        ArtworkDetail artwork = createArtwork("Same text revision identity");
        DescriptionResponse firstApproval = approve(artwork.id(),
                descriptionService.createManual(artwork.id(), "Objective", "A blue square."));
        PublicationResult first = service.publish(artwork.id(), currentVersion(artwork.id()), ADMIN_ID);
        DescriptionResponse sameTextDraft = descriptionService.updateDraft(artwork.id(),
                firstApproval.descriptionId(), "Objective", "A blue square.", firstApproval.version());
        DescriptionResponse secondApproval = approve(artwork.id(), sameTextDraft);

        PublicationResult second = service.publish(artwork.id(), currentVersion(artwork.id()), ADMIN_ID);

        assertThat(secondApproval.approvedRevisionId()).isNotEqualTo(firstApproval.approvedRevisionId());
        assertThat(second.created()).isTrue();
        assertThat(second.publicationId()).isNotEqualTo(first.publicationId());
        assertThat(publicationRepository.countByArtworkId(artwork.id())).isEqualTo(2);
    }

    @Test
    void returningToAnExactApprovedRevisionOrderReusesItsOlderSnapshot() throws Exception {
        ArtworkDetail artwork = createArtwork("Reusable ordered revisions");
        DescriptionResponse objective = approve(artwork.id(),
                descriptionService.createManual(artwork.id(), "Objective", "A blue square."));
        DescriptionResponse subjective = approve(artwork.id(),
                descriptionService.createManual(artwork.id(), "Subjective", "The square feels expansive."));
        PublicationResult original = service.publish(artwork.id(), currentVersion(artwork.id()), ADMIN_ID);

        descriptionService.reorder(artwork.id(),
                List.of(subjective.descriptionId(), objective.descriptionId()), currentVersion(artwork.id()));
        PublicationResult reversed = service.publish(artwork.id(), currentVersion(artwork.id()), ADMIN_ID);
        descriptionService.reorder(artwork.id(),
                List.of(objective.descriptionId(), subjective.descriptionId()), currentVersion(artwork.id()));

        PublicationResult restored = service.publish(artwork.id(), currentVersion(artwork.id()), ADMIN_ID);

        assertThat(reversed.created()).isTrue();
        assertThat(restored.created()).isFalse();
        assertThat(restored.publicationId()).isEqualTo(original.publicationId());
        assertThat(publicationRepository.countByArtworkId(artwork.id())).isEqualTo(2);
        assertThat(publicationRepository.findCurrentByArtworkId(artwork.id())).get()
                .extracting(Publication::getId).isEqualTo(original.publicationId());
    }

    private DescriptionResponse approve(UUID artworkId, DescriptionResponse draft) {
        return descriptionService.approve(artworkId, draft.descriptionId(), "admin", draft.version());
    }

    private long currentVersion(UUID artworkId) {
        return artworkRepository.findById(artworkId).orElseThrow().getVersion();
    }

    private ArtworkDetail createArtwork(String title) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return artworkService.upload(new MockMultipartFile("image", "art.png", "image/png", bytes.toByteArray()),
                title, "A. Artist", null);
    }
}
