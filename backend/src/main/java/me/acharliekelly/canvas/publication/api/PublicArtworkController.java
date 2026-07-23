package me.acharliekelly.canvas.publication.api;

import me.acharliekelly.canvas.publication.PublicationService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/artworks/{slug}")
public class PublicArtworkController {
    private final PublicationService service;

    PublicArtworkController(PublicationService service) {
        this.service = service;
    }

    @GetMapping
    PublicArtworkResponse artwork(@PathVariable String slug) {
        return service.publicArtwork(slug);
    }

    @GetMapping("/image")
    ResponseEntity<InputStreamResource> image(@PathVariable String slug) {
        PublicationService.PublicImage image = service.publicImage(slug);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(image.mediaType()))
                .contentLength(image.byteSize())
                .body(new InputStreamResource(image.content()));
    }
}
