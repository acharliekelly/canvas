package org.canvas.artwork.api;

import java.time.Instant;
import java.util.UUID;
import org.canvas.artwork.Artwork;

public record ArtworkDetail(UUID id, String title, String credit, String context, String status,
        String mediaType, long byteSize, long version, Instant createdAt, Instant updatedAt) {
    public static ArtworkDetail from(Artwork artwork) {
        return new ArtworkDetail(artwork.getId(), artwork.getTitle(), artwork.getCredit(), artwork.getContext(),
                artwork.getLifecycleStatus().name(), artwork.getMediaType(), artwork.getByteSize(),
                artwork.getVersion(), artwork.getCreatedAt(), artwork.getUpdatedAt());
    }
}
