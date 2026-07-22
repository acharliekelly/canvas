package org.canvas.publication;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.canvas.artwork.Artwork;
import org.canvas.artwork.ArtworkRepository;
import org.canvas.description.Description;
import org.canvas.description.DescriptionRepository;
import org.canvas.description.DescriptionRevision;
import org.canvas.description.RevisionState;
import org.canvas.publication.api.PublicArtworkResponse;
import org.canvas.storage.ObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicationService {
    private static final int MAX_SLUG_TITLE_LENGTH = 180;

    private final PublicationRepository repository;
    private final ArtworkRepository artworkRepository;
    private final DescriptionRepository descriptionRepository;
    private final ObjectStorage storage;

    PublicationService(PublicationRepository repository, ArtworkRepository artworkRepository,
            DescriptionRepository descriptionRepository, ObjectStorage storage) {
        this.repository = repository;
        this.artworkRepository = artworkRepository;
        this.descriptionRepository = descriptionRepository;
        this.storage = storage;
    }

    @Transactional
    public PublicationResult publish(UUID artworkId, long version, UUID administratorId) {
        Artwork artwork = artworkRepository.findByIdForUpdate(artworkId)
                .orElseThrow(() -> new PublicationProblem("artwork_not_found", "Artwork was not found."));
        if (version < 0 || artwork.getVersion() != version) {
            throw new PublicationProblem("stale_version",
                    "This artwork changed after it was loaded. Refresh and try again.");
        }
        Objects.requireNonNull(administratorId, "administratorId");

        List<ApprovedSnapshot> approved = approvedSnapshots(artworkId);
        if (approved.isEmpty()) {
            throw new PublicationNotAllowed("Approve at least one description before publishing this artwork.");
        }

        String hash = contentHash(artwork, approved);
        Publication current = repository.findCurrentByArtworkId(artworkId).orElse(null);
        Publication publication = repository.findByArtworkIdAndContentHash(artworkId, hash).orElse(null);
        boolean created = publication == null;

        if (created) {
            publication = new Publication(artwork, repository.maximumVersion(artworkId) + 1,
                    hash, administratorId, Instant.now().truncatedTo(ChronoUnit.MICROS));
            for (int index = 0; index < approved.size(); index++) {
                ApprovedSnapshot snapshot = approved.get(index);
                publication.addDescription(snapshot.revisionId(), index, snapshot.label(), snapshot.text());
            }
        }

        if (current != null && !current.getId().equals(publication.getId())) {
            current.markSuperseded();
            repository.save(current);
            repository.flush();
        }
        publication.markCurrent();

        String slug = artwork.getPublicSlug() == null ? slugFor(artwork) : artwork.getPublicSlug();
        artwork.markPublished(slug);
        Publication saved = repository.saveAndFlush(publication);
        artworkRepository.saveAndFlush(artwork);
        return result(saved, artwork.getVersion(), created);
    }

    @Transactional(readOnly = true)
    public PublicArtworkResponse publicArtwork(String slug) {
        Publication publication = currentBySlug(slug);
        return PublicArtworkResponse.from(publication, "/public/artworks/" + slug + "/image");
    }

    @Transactional(readOnly = true)
    public PublicImage publicImage(String slug) {
        Publication publication = currentBySlug(slug);
        try {
            return new PublicImage(storage.get(publication.getImageObjectKey()),
                    publication.getImageMediaType(), publication.getImageByteSize());
        } catch (RuntimeException error) {
            throw new PublicationProblem("public_image_unavailable",
                    "The published artwork image is temporarily unavailable.", error);
        }
    }

    private Publication currentBySlug(String slug) {
        return repository.findCurrentBySlug(slug)
                .orElseThrow(() -> new PublicationProblem("public_artwork_not_found",
                        "Published artwork was not found."));
    }

    private List<ApprovedSnapshot> approvedSnapshots(UUID artworkId) {
        return descriptionRepository.findAllByArtworkIdOrderByDisplayOrderAsc(artworkId).stream()
                .map(this::latestApproved)
                .filter(Objects::nonNull)
                .toList();
    }

    private ApprovedSnapshot latestApproved(Description description) {
        List<DescriptionRevision> revisions = description.getRevisions();
        for (int index = revisions.size() - 1; index >= 0; index--) {
            DescriptionRevision revision = revisions.get(index);
            if (revision.getState() == RevisionState.APPROVED) {
                return new ApprovedSnapshot(revision.getId(), revision.getLabel(), revision.getText());
            }
        }
        return null;
    }

    private static PublicationResult result(Publication publication, long artworkVersion, boolean created) {
        return new PublicationResult(publication.getId(), publication.getArtwork().getPublicSlug(),
                publication.getPublishedAt(), artworkVersion, created,
                publication.getDescriptions().stream()
                        .map(item -> new PublishedDescriptionResult(item.getLabel(), item.getText()))
                        .toList());
    }

    private static String slugFor(Artwork artwork) {
        String titlePart = Normalizer.normalize(artwork.getTitle(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (titlePart.isEmpty()) {
            titlePart = "artwork";
        }
        if (titlePart.length() > MAX_SLUG_TITLE_LENGTH) {
            titlePart = titlePart.substring(0, MAX_SLUG_TITLE_LENGTH).replaceAll("-+$", "");
        }
        return titlePart + "-" + artwork.getId().toString().toLowerCase(java.util.Locale.ROOT);
    }

    private static String contentHash(Artwork artwork, List<ApprovedSnapshot> approved) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, artwork.getTitle());
            update(digest, artwork.getCredit());
            update(digest, artwork.getImageObjectKey());
            update(digest, artwork.getMediaType());
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(artwork.getByteSize()).array());
            for (int index = 0; index < approved.size(); index++) {
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(index).array());
                ApprovedSnapshot snapshot = approved.get(index);
                digest.update(ByteBuffer.allocate(2 * Long.BYTES)
                        .putLong(snapshot.revisionId().getMostSignificantBits())
                        .putLong(snapshot.revisionId().getLeastSignificantBits())
                        .array());
                update(digest, snapshot.label());
                update(digest, snapshot.text());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform.", impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private record ApprovedSnapshot(UUID revisionId, String label, String text) {}

    public record PublicationResult(UUID publicationId, String slug, Instant publishedAt,
            long artworkVersion, boolean created, List<PublishedDescriptionResult> descriptions) {}

    public record PublishedDescriptionResult(String label, String text) {}

    public record PublicImage(InputStream content, String mediaType, long byteSize) {}

    public static class PublicationProblem extends RuntimeException {
        private final String code;

        PublicationProblem(String code, String message) {
            super(message);
            this.code = code;
        }

        PublicationProblem(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String getCode() { return code; }
    }

    public static final class PublicationNotAllowed extends PublicationProblem {
        PublicationNotAllowed(String message) {
            super("publication_not_allowed", message);
        }
    }
}
