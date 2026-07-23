package me.acharliekelly.canvas.publication.api;

import java.util.List;
import me.acharliekelly.canvas.publication.Publication;

public record PublicArtworkResponse(String title, String credit, String imageUrl, String qrUrl,
        List<PublicDescriptionResponse> descriptions) {
    public static PublicArtworkResponse from(Publication publication, String slug) {
        String basePath = "/public/artworks/" + slug;
        String qrUrl = publication.getQrAsset() == null ? null
                : basePath + "/qr/" + publication.getQrAsset().getId();
        return new PublicArtworkResponse(publication.getTitle(), publication.getCredit(), basePath + "/image", qrUrl,
                publication.getDescriptions().stream()
                        .map(item -> new PublicDescriptionResponse(item.getLabel(), item.getText(),
                                item.getAudioAsset() == null ? null
                                        : basePath + "/descriptions/" + item.getId()
                                                + "/audio/" + item.getAudioAsset().getId()))
                        .toList());
    }

    public record PublicDescriptionResponse(String label, String text, String audioUrl) {}
}
