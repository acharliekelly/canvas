package me.acharliekelly.canvas.artwork;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import me.acharliekelly.canvas.artwork.api.ArtworkDetail;
import me.acharliekelly.canvas.artwork.api.ArtworkSummary;
import me.acharliekelly.canvas.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ArtworkService {
    private static final Logger log = LoggerFactory.getLogger(ArtworkService.class);
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/png", "image/jpeg");
    private static final int MAX_METADATA_LENGTH = 255;

    private final ArtworkRepository repository;
    private final ObjectStorage storage;
    private final TransactionTemplate transactions;
    private final long maxBytes;

    ArtworkService(ArtworkRepository repository, @Qualifier("originalObjectStorage") ObjectStorage storage,
            PlatformTransactionManager transactionManager,
            @Value("${canvas.upload-max-size}") DataSize maximumSize) {
        this.repository = repository;
        this.storage = storage;
        this.transactions = new TransactionTemplate(transactionManager);
        this.maxBytes = maximumSize.toBytes();
    }

    /**
     * Validates metadata and decodes the image before storing its original bytes. Storage precedes
     * metadata persistence because the row must reference an existing object; if persistence then
     * fails, this method performs best-effort storage compensation without exposing the object key
     * to the caller. A cleanup failure is logged and never replaces the original persistence error.
     */
    public ArtworkDetail upload(MultipartFile image, String title, String credit, String context) {
        String normalizedTitle = requiredMetadata(title, "title", "Title");
        String normalizedCredit = requiredMetadata(credit, "credit", "Artist or display credit");
        validateImage(image);

        ObjectStorage.StoredObject stored;
        try (InputStream content = image.getInputStream()) {
            stored = storage.put(content, image.getSize(), image.getContentType());
        } catch (IOException | RuntimeException error) {
            throw new ArtworkProblem("storage_unavailable", "Artwork storage is unavailable.", "image", error);
        }

        try {
            Artwork artwork = Objects.requireNonNull(transactions.execute(status -> repository.saveAndFlush(
                    new Artwork(normalizedTitle, normalizedCredit, blankToNull(context), image.getContentType(),
                            image.getSize(), stored.objectKey()))));
            return ArtworkDetail.from(artwork);
        } catch (RuntimeException persistenceFailure) {
            try {
                storage.delete(stored.objectKey());
            } catch (RuntimeException compensationFailure) {
                // Preserve the actionable persistence failure; cleanup is best-effort only.
                persistenceFailure.addSuppressed(compensationFailure);
                log.error("Failed to delete object after artwork persistence failure", compensationFailure);
            }
            log.error("Failed to persist uploaded artwork metadata", persistenceFailure);
            throw new ArtworkProblem("persistence_unavailable", "Artwork metadata could not be saved.", "image",
                    persistenceFailure);
        }
    }

    @Transactional(readOnly = true)
    public List<ArtworkSummary> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(ArtworkSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public ArtworkDetail detail(UUID id) {
        return repository.findById(id).map(ArtworkDetail::from)
                .orElseThrow(() -> new ArtworkProblem("artwork_not_found", "Artwork was not found."));
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ArtworkProblem("image_required", "Choose a PNG or JPEG image.", "image");
        }
        if (!SUPPORTED_TYPES.contains(image.getContentType())) {
            throw new ArtworkProblem("unsupported_media_type", "Only PNG and JPEG images are supported.", "image");
        }
        if (image.getSize() > maxBytes) {
            throw new ArtworkProblem("image_too_large", "The image exceeds the configured upload limit.", "image");
        }
        try (InputStream content = image.getInputStream()) {
            BufferedImage decoded = ImageIO.read(content);
            if (decoded == null) {
                throw new ArtworkProblem("invalid_image", "The uploaded file is not a decodable image.", "image");
            }
        } catch (IOException error) {
            throw new ArtworkProblem("invalid_image", "The uploaded file is not a decodable image.", "image", error);
        }
    }

    private static String requiredMetadata(String value, String field, String label) {
        if (value == null || value.isBlank()) {
            throw new ArtworkProblem("invalid_request", label + " is required.", field);
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_METADATA_LENGTH) {
            throw new ArtworkProblem(field + "_too_long", label + " must be 255 characters or fewer.", field);
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static class ArtworkProblem extends RuntimeException {
        private final String code;
        private final String field;

        ArtworkProblem(String code, String message) { this(code, message, null, null); }
        ArtworkProblem(String code, String message, String field) { this(code, message, field, null); }
        ArtworkProblem(String code, String message, String field, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.field = field;
        }
        public String getCode() { return code; }
        public String getField() { return field; }
    }
}
