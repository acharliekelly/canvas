package org.canvas.publication.api;

import java.util.List;
import org.canvas.publication.Publication;

public record PublicArtworkResponse(String title, String credit, String imageUrl,
        List<PublicDescriptionResponse> descriptions) {
    public static PublicArtworkResponse from(Publication publication, String imageUrl) {
        return new PublicArtworkResponse(publication.getTitle(), publication.getCredit(), imageUrl,
                publication.getDescriptions().stream()
                        .map(item -> new PublicDescriptionResponse(item.getLabel(), item.getText()))
                        .toList());
    }

    public record PublicDescriptionResponse(String label, String text) {}
}
