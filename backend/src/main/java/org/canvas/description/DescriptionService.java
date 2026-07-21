package org.canvas.description;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.canvas.artwork.Artwork;
import org.canvas.artwork.ArtworkRepository;
import org.canvas.description.api.DescriptionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DescriptionService {
    private static final int MAX_LABEL_LENGTH = 255;

    private final DescriptionRepository repository;
    private final ArtworkRepository artworkRepository;

    DescriptionService(DescriptionRepository repository, ArtworkRepository artworkRepository) {
        this.repository = repository;
        this.artworkRepository = artworkRepository;
    }

    @Transactional(readOnly = true)
    public List<DescriptionResponse> listForArtwork(UUID artworkId) {
        requireArtwork(artworkId);
        return responses(repository.findAllByArtworkIdOrderByDisplayOrderAsc(artworkId));
    }

    @Transactional
    public DescriptionResponse createManual(UUID artworkId, String label, String text) {
        return createDraft(artworkId, label, text, DescriptionSource.MANUAL);
    }

    @Transactional
    public DescriptionResponse createGeneratedDraft(UUID artworkId, String label, String text) {
        return createDraft(artworkId, label, text, DescriptionSource.GENERATED);
    }

    private DescriptionResponse createDraft(UUID artworkId, String label, String text, DescriptionSource source) {
        String normalizedLabel = requiredLabel(label);
        String normalizedText = requiredText(text);
        Artwork artwork = requireArtwork(artworkId);
        Description description = new Description(
                artwork, source, repository.maximumDisplayOrder(artworkId) + 1);
        repository.saveAndFlush(description);
        description.startDraft(normalizedLabel, normalizedText, null);
        return DescriptionResponse.from(repository.saveAndFlush(description));
    }

    @Transactional
    public DescriptionResponse updateDraft(UUID artworkId, UUID descriptionId,
            String label, String text, Long expectedVersion) {
        String normalizedLabel = requiredLabel(label);
        String normalizedText = requiredText(text);
        Description description = requireOwnedDescription(artworkId, descriptionId);
        requireCurrentVersion(description, expectedVersion);

        DescriptionRevision current = description.getCurrentRevision();
        if (current.getState() == RevisionState.APPROVED) {
            description.startDraft(normalizedLabel, normalizedText, current);
        } else {
            description.updateCurrentDraft(normalizedLabel, normalizedText);
        }
        return DescriptionResponse.from(repository.saveAndFlush(description));
    }

    @Transactional
    public DescriptionResponse approve(UUID artworkId, UUID descriptionId,
            String approver, Long expectedVersion) {
        Description description = requireOwnedDescription(artworkId, descriptionId);
        requireCurrentVersion(description, expectedVersion);
        if (description.getCurrentRevision().getState() == RevisionState.DRAFT) {
            description.approveCurrent(requiredApprover(approver), Instant.now());
        }
        return DescriptionResponse.from(repository.saveAndFlush(description));
    }

    @Transactional
    public List<DescriptionResponse> reorder(UUID artworkId, List<UUID> requestedIds, Long expectedArtworkVersion) {
        List<Description> current = repository.findAllByArtworkIdOrderByDisplayOrderAsc(artworkId);
        Artwork artwork = requireArtwork(artworkId);
        validateOrder(current, requestedIds);
        if (expectedArtworkVersion == null || expectedArtworkVersion < 0
                || artworkRepository.advanceVersion(artworkId, expectedArtworkVersion, Instant.now()) != 1) {
            throw new DescriptionProblem("stale_version",
                    "This artwork changed after it was loaded. Refresh and try again.");
        }

        repository.moveOrdersOutOfTheWay(artworkId);
        for (int index = 0; index < requestedIds.size(); index++) {
            if (repository.setDisplayOrder(artworkId, requestedIds.get(index), index) != 1) {
                throw new DescriptionProblem("invalid_description_order",
                        "Description order must contain every current description exactly once.", "descriptionIds");
            }
        }
        return responses(repository.findAllByArtworkIdOrderByDisplayOrderAsc(artworkId));
    }

    private void validateOrder(List<Description> current, List<UUID> requestedIds) {
        if (requestedIds == null || requestedIds.size() != current.size()
                || new HashSet<>(requestedIds).size() != requestedIds.size()
                || !new HashSet<>(requestedIds).equals(
                        current.stream().map(Description::getId).collect(java.util.stream.Collectors.toSet()))) {
            throw new DescriptionProblem("invalid_description_order",
                    "Description order must contain every current description exactly once.", "descriptionIds");
        }
    }

    private Artwork requireArtwork(UUID artworkId) {
        return artworkRepository.findById(artworkId)
                .orElseThrow(() -> new DescriptionProblem("artwork_not_found", "Artwork was not found."));
    }

    private Description requireOwnedDescription(UUID artworkId, UUID descriptionId) {
        return repository.findByIdAndArtworkId(descriptionId, artworkId)
                .orElseThrow(() -> new DescriptionProblem("description_not_found", "Description was not found."));
    }

    private void requireCurrentVersion(Description description, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0 || description.getVersion() != expectedVersion) {
            throw new DescriptionProblem("stale_version",
                    "This description changed after it was loaded. Refresh and try again.");
        }
    }

    private static String requiredLabel(String value) {
        if (value == null || value.isBlank()) {
            throw new DescriptionProblem("invalid_description", "Description label is required.", "label");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_LABEL_LENGTH) {
            throw new DescriptionProblem("invalid_description",
                    "Description label must be 255 characters or fewer.", "label");
        }
        return normalized;
    }

    private static String requiredText(String value) {
        if (value == null || value.isBlank()) {
            throw new DescriptionProblem("invalid_description", "Description text is required.", "text");
        }
        return value.trim();
    }

    private static String requiredApprover(String value) {
        if (value == null || value.isBlank()) {
            throw new DescriptionProblem("invalid_approver", "An authenticated approver is required.");
        }
        return value;
    }

    private static List<DescriptionResponse> responses(List<Description> descriptions) {
        return descriptions.stream().map(DescriptionResponse::from).toList();
    }

    public static class DescriptionProblem extends RuntimeException {
        private final String code;
        private final String field;

        DescriptionProblem(String code, String message) {
            this(code, message, null);
        }

        DescriptionProblem(String code, String message, String field) {
            super(message);
            this.code = code;
            this.field = field;
        }

        public String getCode() { return code; }
        public String getField() { return field; }
    }
}
