package org.canvas.publication.api;

import java.util.List;
import org.canvas.publication.Publication;

public record PublicArtworkResponse(String title, String credit, String imageUrl,
        List<PublicDescriptionResponse> descriptions) {
    public static PublicArtworkResponse from(Publication publication, String slug) {
        String basePath = "/public/artworks/" + slug;
        return new PublicArtworkResponse(publication.getTitle(), publication.getCredit(), basePath + "/image",
                publication.getDescriptions().stream()
                        .map(item -> new PublicDescriptionResponse(item.getLabel(), item.getText(),
                                basePath + "/descriptions/" + item.getId() + "/audio"))
                        .toList());
    }

    public record PublicDescriptionResponse(String label, String text, String audioUrl) {}
}
