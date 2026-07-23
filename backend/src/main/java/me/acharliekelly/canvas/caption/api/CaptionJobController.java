package me.acharliekelly.canvas.caption.api;

import java.net.URI;
import java.util.UUID;
import me.acharliekelly.canvas.caption.CaptionJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artworks/{artworkId}/caption-jobs")
public class CaptionJobController {
    private final CaptionJobService service;

    CaptionJobController(CaptionJobService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<CaptionJobResponse> request(@PathVariable UUID artworkId) {
        CaptionJobResponse job = service.request(artworkId);
        return ResponseEntity.accepted()
                .location(URI.create("/api/artworks/" + artworkId + "/caption-jobs/" + job.jobId()))
                .body(job);
    }

    @GetMapping("/{jobId}")
    CaptionJobResponse get(@PathVariable UUID artworkId, @PathVariable UUID jobId) {
        return service.get(artworkId, jobId);
    }
}
