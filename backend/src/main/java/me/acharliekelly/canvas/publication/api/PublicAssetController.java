package me.acharliekelly.canvas.publication.api;

import java.util.UUID;
import me.acharliekelly.canvas.publication.PublicationService;
import me.acharliekelly.canvas.publication.PublicationService.PublicAsset;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/artworks/{slug}")
public class PublicAssetController {
    private static final String IMMUTABLE_PUBLIC_CACHE = "public, max-age=31536000, immutable";

    private final PublicationService service;

    PublicAssetController(PublicationService service) {
        this.service = service;
    }

    @GetMapping("/descriptions/{publishedDescriptionId}/audio/{assetId}")
    ResponseEntity<InputStreamResource> audio(@PathVariable String slug,
            @PathVariable UUID publishedDescriptionId, @PathVariable UUID assetId) {
        return response(service.publicAudio(slug, publishedDescriptionId, assetId), null);
    }

    @GetMapping("/qr/{assetId}")
    ResponseEntity<InputStreamResource> qr(@PathVariable String slug, @PathVariable UUID assetId) {
        return response(service.publicQr(slug, assetId),
                "attachment; filename=\"" + safeFilename(slug) + "-qr.png\"");
    }

    private ResponseEntity<InputStreamResource> response(PublicAsset asset, String contentDisposition) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, IMMUTABLE_PUBLIC_CACHE)
                .header(HttpHeaders.ETAG, "\"" + asset.inputKey() + "\"")
                .contentType(MediaType.parseMediaType(asset.mediaType()))
                .contentLength(asset.byteSize());
        if (contentDisposition != null) {
            response.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
        }
        return response.body(new InputStreamResource(asset.content()));
    }

    private static String safeFilename(String slug) {
        return slug.replaceAll("[^a-z0-9-]", "-");
    }
}
