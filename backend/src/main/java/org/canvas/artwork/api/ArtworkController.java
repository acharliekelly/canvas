package org.canvas.artwork.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.canvas.artwork.ArtworkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/artworks")
public class ArtworkController {
    private final ArtworkService service;

    ArtworkController(ArtworkService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ArtworkDetail> upload(@RequestParam MultipartFile image, @RequestParam String title,
            @RequestParam String credit, @RequestParam(required = false) String context) {
        ArtworkDetail created = service.upload(image, title, credit, context);
        return ResponseEntity.created(URI.create("/api/artworks/" + created.id())).body(created);
    }

    @GetMapping
    List<ArtworkSummary> list() {
        return service.list();
    }

    @GetMapping("/{artworkId}")
    ArtworkDetail detail(@PathVariable UUID artworkId) {
        return service.detail(artworkId);
    }
}
