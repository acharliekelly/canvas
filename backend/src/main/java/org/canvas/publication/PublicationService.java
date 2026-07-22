package org.canvas.publication;

import java.io.InputStream;
import java.net.URI;
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
import org.canvas.publication.asset.AssetService;
import org.canvas.publication.asset.AssetKind;
import org.canvas.publication.asset.AudioGenerator.ApprovedDescriptionInput;
import org.canvas.publication.asset.GeneratedAsset;
import org.canvas.publication.api.PublicArtworkResponse;
import org.canvas.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicationService {
    private static final int MAX_SLUG_TITLE_LENGTH = 180;

    private final PublicationRepository repository;
    private final ArtworkRepository artworkRepository;
    private final DescriptionRepository descriptionRepository;
    private final ObjectStorage storage;
    private final AssetService assets;
    private final URI publicBaseUri;

    public PublicationService(PublicationRepository repository, ArtworkRepository artworkRepository,
            DescriptionRepository descriptionRepository,
            @Qualifier("originalObjectStorage") ObjectStorage storage, AssetService assets,
            @Value("${canvas.public-base-url}") URI publicBaseUri) {
        this.repository = repository;
        this.artworkRepository = artworkRepository;
        this.descriptionRepository = descriptionRepository;
        this.storage = storage;
        this.assets = assets;
        this.publicBaseUri = publicBaseUri;
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
        boolean created = current == null || !current.getContentHash().equals(hash);
        Publication publication = created ? null : current;
        String slug = artwork.getPublicSlug() == null ? slugFor(artwork) : artwork.getPublicSlug();

        if (created) {
            publication = new Publication(artwork, repository.maximumVersion(artworkId) + 1,
                    hash, administratorId, Instant.now().truncatedTo(ChronoUnit.MICROS));
            for (int index = 0; index < approved.size(); index++) {
                ApprovedSnapshot snapshot = approved.get(index);
                publication.addDescription(snapshot.revisionId(), index, snapshot.label(), snapshot.text());
            }
            publication = repository.saveAndFlush(publication);
        }

        try {
            List<PublishedDescription> publishedDescriptions = publication.getDescriptions();
            for (int index = 0; index < publishedDescriptions.size(); index++) {
                PublishedDescription description = publishedDescriptions.get(index);
                ApprovedDescriptionInput input = approved.get(index).audioInput();
                GeneratedAsset audio = description.getAudioAsset() == null
                        ? assets.audioFor(input)
                        : assets.ensureAudio(description.getAudioAsset(), input);
                description.associateAudioAsset(audio);
            }
            GeneratedAsset qr = publication.getQrAsset() == null
                    ? assets.qrFor(publicUri(slug), publication.getId())
                    : assets.ensureQr(publication.getQrAsset(), publicUri(slug));
            publication.associateQrAsset(qr);
            publication = repository.saveAndFlush(publication);
        } catch (RuntimeException error) {
            throw new PublicationProblem("asset_generation_unavailable",
                    "Publication assets could not be prepared. Try publishing again.", error);
        }

        if (current != null && !current.getId().equals(publication.getId())) {
            current.markSuperseded();
            repository.save(current);
            repository.flush();
        }
        publication.markCurrent();

        artwork.markPublished(slug);
        Publication saved = repository.saveAndFlush(publication);
        artworkRepository.saveAndFlush(artwork);
        return result(saved, artwork.getVersion(), created);
    }

    @Transactional(readOnly = true)
    public PublicArtworkResponse publicArtwork(String slug) {
        Publication publication = currentBySlug(slug);
        return PublicArtworkResponse.from(publication, slug);
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

    @Transactional(readOnly = true)
    public PublicAsset publicAudio(String slug, UUID publishedDescriptionId, UUID assetId) {
        Publication publication = currentBySlug(slug);
        PublishedDescription description = publication.getDescriptions().stream()
                .filter(candidate -> candidate.getId().equals(publishedDescriptionId))
                .findFirst()
                .orElseThrow(() -> new PublicationProblem("public_asset_not_found",
                        "Published audio was not found."));
        GeneratedAsset asset = description.getAudioAsset();
        if (asset == null || asset.getKind() != AssetKind.AUDIO) {
            throw new PublicationProblem("public_asset_unavailable",
                    "The published asset is temporarily unavailable.");
        }
        if (!asset.getId().equals(assetId)) {
            throw new PublicationProblem("public_asset_not_found", "Published audio was not found.");
        }
        return publicAsset(asset);
    }

    @Transactional(readOnly = true)
    public PublicAsset publicQr(String slug, UUID assetId) {
        Publication publication = currentBySlug(slug);
        GeneratedAsset asset = publication.getQrAsset();
        if (asset == null || asset.getKind() != AssetKind.QR_CODE) {
            throw new PublicationProblem("public_asset_unavailable",
                    "The published asset is temporarily unavailable.");
        }
        if (!asset.getId().equals(assetId)) {
            throw new PublicationProblem("public_asset_not_found", "Published QR code was not found.");
        }
        return publicAsset(asset);
    }

    private PublicAsset publicAsset(GeneratedAsset asset) {
        try {
            return new PublicAsset(assets.content(asset), asset.getMediaType(), asset.getByteSize(),
                    asset.getInputKey());
        } catch (RuntimeException error) {
            throw new PublicationProblem("public_asset_unavailable",
                    "The published asset is temporarily unavailable.", error);
        }
    }

    private URI publicUri(String slug) {
        String base = publicBaseUri.toASCIIString().replaceAll("/+$", "");
        return URI.create(base + "/artworks/" + slug);
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
        String qrUrl = publication.getQrAsset() == null ? null
                : "/public/artworks/" + publication.getArtwork().getPublicSlug()
                        + "/qr/" + publication.getQrAsset().getId();
        return new PublicationResult(publication.getId(), publication.getArtwork().getPublicSlug(),
                publication.getPublishedAt(), artworkVersion, created, qrUrl,
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

    private record ApprovedSnapshot(UUID revisionId, String label, String text) {
        ApprovedDescriptionInput audioInput() {
            return new ApprovedDescriptionInput(revisionId, label, text);
        }
    }

    public record PublicationResult(UUID publicationId, String slug, Instant publishedAt,
            long artworkVersion, boolean created, String qrUrl,
            List<PublishedDescriptionResult> descriptions) {}

    public record PublishedDescriptionResult(String label, String text) {}

    public record PublicImage(InputStream content, String mediaType, long byteSize) {}

    public record PublicAsset(InputStream content, String mediaType, long byteSize, String inputKey) {}

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
