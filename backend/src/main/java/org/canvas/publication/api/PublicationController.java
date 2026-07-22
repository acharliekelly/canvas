package org.canvas.publication.api;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;
import org.canvas.publication.PublicationService;
import org.canvas.publication.PublicationService.PublicationResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artworks/{artworkId}/publication")
public class PublicationController {
    private final PublicationService service;

    PublicationController(PublicationService service) {
        this.service = service;
    }

    @PostMapping
    PublicationResult publish(@PathVariable UUID artworkId, @RequestBody PublicationRequest request,
            Principal principal) {
        UUID administratorId = UUID.nameUUIDFromBytes(
                ("canvas-administrator:" + principal.getName()).getBytes(StandardCharsets.UTF_8));
        return service.publish(artworkId, request.version(), administratorId);
    }

    record PublicationRequest(long version) {}
}
