package org.canvas.description.api;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.canvas.description.DescriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artworks/{artworkId}")
public class DescriptionController {
    private final DescriptionService service;

    DescriptionController(DescriptionService service) {
        this.service = service;
    }

    @GetMapping("/descriptions")
    List<DescriptionResponse> list(@PathVariable UUID artworkId) {
        return service.listForArtwork(artworkId);
    }

    @PostMapping("/descriptions")
    ResponseEntity<DescriptionResponse> create(@PathVariable UUID artworkId,
            @RequestBody CreateDescriptionRequest request) {
        DescriptionResponse created = service.createManual(artworkId, request.label(), request.text());
        return ResponseEntity.created(URI.create("/api/artworks/" + artworkId + "/descriptions/"
                + created.descriptionId())).body(created);
    }

    @PutMapping("/descriptions/{descriptionId}/draft")
    DescriptionResponse updateDraft(@PathVariable UUID artworkId, @PathVariable UUID descriptionId,
            @RequestBody UpdateDraftRequest request) {
        return service.updateDraft(artworkId, descriptionId,
                request.label(), request.text(), request.version());
    }

    @PutMapping("/description-order")
    List<DescriptionResponse> reorder(@PathVariable UUID artworkId,
            @RequestBody ReorderDescriptionsRequest request) {
        return service.reorder(artworkId, request.descriptionIds(), request.version());
    }

    @PostMapping("/descriptions/{descriptionId}/approve")
    DescriptionResponse approve(@PathVariable UUID artworkId, @PathVariable UUID descriptionId,
            @RequestBody ApproveDescriptionRequest request, Principal principal) {
        return service.approve(artworkId, descriptionId, principal.getName(), request.version());
    }
}
