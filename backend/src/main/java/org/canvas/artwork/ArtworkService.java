package org.canvas.artwork;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.canvas.artwork.api.ArtworkDetail;
import org.canvas.artwork.api.ArtworkSummary;
import org.canvas.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ArtworkService {
    private static final Logger log = LoggerFactory.getLogger(ArtworkService.class);
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/png", "image/jpeg");

    private final ArtworkRepository repository;
    private final ObjectStorage storage;
    private final long maxBytes;

    ArtworkService(ArtworkRepository repository, ObjectStorage storage,
            @Value("${canvas.upload-max-size}") DataSize maximumSize) {
        this.repository = repository;
        this.storage = storage;
        this.maxBytes = maximumSize.toBytes();
    }

    @Transactional
    public ArtworkDetail upload(MultipartFile image, String title, String credit, String context) {
        String normalizedTitle = required(title, "title");
        String normalizedCredit = required(credit, "credit");
        validateImage(image);

        ObjectStorage.StoredObject stored;
        try (InputStream content = image.getInputStream()) {
            stored = storage.put(content, image.getSize(), image.getContentType());
        } catch (IOException | RuntimeException error) {
            throw new ArtworkProblem("storage_unavailable", "Artwork storage is unavailable.", error);
        }

        try {
            Artwork artwork = repository.saveAndFlush(new Artwork(normalizedTitle, normalizedCredit,
                    blankToNull(context), image.getContentType(), image.getSize(), stored.objectKey()));
            return ArtworkDetail.from(artwork);
        } catch (RuntimeException persistenceFailure) {
            try {
                storage.delete(stored.objectKey());
            } catch (RuntimeException compensationFailure) {
                persistenceFailure.addSuppressed(compensationFailure);
                log.error("Failed to delete object after artwork persistence failure", compensationFailure);
            }
            log.error("Failed to persist uploaded artwork metadata", persistenceFailure);
            throw new ArtworkProblem("persistence_unavailable", "Artwork metadata could not be saved.", persistenceFailure);
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
            throw new ArtworkProblem("image_required", "Choose a PNG or JPEG image.");
        }
        if (!SUPPORTED_TYPES.contains(image.getContentType())) {
            throw new ArtworkProblem("unsupported_media_type", "Only PNG and JPEG images are supported.");
        }
        if (image.getSize() > maxBytes) {
            throw new ArtworkProblem("image_too_large", "The image exceeds the configured upload limit.");
        }
        try (InputStream content = image.getInputStream()) {
            BufferedImage decoded = ImageIO.read(content);
            if (decoded == null) {
                throw new ArtworkProblem("invalid_image", "The uploaded file is not a decodable image.");
            }
        } catch (IOException error) {
            throw new ArtworkProblem("invalid_image", "The uploaded file is not a decodable image.", error);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ArtworkProblem("invalid_request", field + " is required.");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static class ArtworkProblem extends RuntimeException {
        private final String code;

        ArtworkProblem(String code, String message) { super(message); this.code = code; }
        ArtworkProblem(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
        public String getCode() { return code; }
    }
}
