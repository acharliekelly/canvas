package org.canvas.description.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.canvas.description.Description;
import org.canvas.description.DescriptionRevision;
import org.canvas.description.DescriptionSource;
import org.canvas.description.RevisionState;

public record DescriptionResponse(
        UUID descriptionId,
        UUID artworkId,
        DescriptionSource source,
        int displayOrder,
        long version,
        RevisionResponse currentRevision,
        UUID approvedRevisionId,
        List<RevisionResponse> revisions,
        Instant createdAt,
        Instant updatedAt) {

    public static DescriptionResponse from(Description description) {
        DescriptionRevision current = description.getCurrentRevision();
        List<RevisionResponse> history = description.getRevisions().stream()
                .map(RevisionResponse::from)
                .toList();
        UUID approvedRevisionId = current.getState() == RevisionState.APPROVED
                ? current.getId()
                : history.reversed().stream()
                        .filter(revision -> revision.state() == RevisionState.APPROVED)
                        .map(RevisionResponse::revisionId)
                        .findFirst()
                        .orElse(null);
        return new DescriptionResponse(description.getId(), description.getArtwork().getId(),
                description.getSource(), description.getDisplayOrder(), description.getVersion(),
                RevisionResponse.from(current), approvedRevisionId, history,
                description.getCreatedAt(), description.getUpdatedAt());
    }

    public record RevisionResponse(
            UUID revisionId,
            String label,
            String text,
            RevisionState state,
            UUID parentRevisionId,
            String approvedBy,
            Instant approvedAt,
            Instant createdAt,
            Instant updatedAt) {

        static RevisionResponse from(DescriptionRevision revision) {
            return new RevisionResponse(revision.getId(), revision.getLabel(), revision.getText(),
                    revision.getState(), revision.getParentRevision() == null
                            ? null : revision.getParentRevision().getId(),
                    revision.getApprovedBy(), revision.getApprovedAt(),
                    revision.getCreatedAt(), revision.getUpdatedAt());
        }
    }
}
