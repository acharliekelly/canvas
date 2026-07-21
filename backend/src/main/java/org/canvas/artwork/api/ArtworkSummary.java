package org.canvas.artwork.api;

import java.time.Instant;
import java.util.UUID;
import org.canvas.artwork.Artwork;

public record ArtworkSummary(UUID id, String title, String credit, String status, Instant createdAt) {
    public static ArtworkSummary from(Artwork artwork) {
        return new ArtworkSummary(artwork.getId(), artwork.getTitle(), artwork.getCredit(),
                artwork.getLifecycleStatus().name(), artwork.getCreatedAt());
    }
}
