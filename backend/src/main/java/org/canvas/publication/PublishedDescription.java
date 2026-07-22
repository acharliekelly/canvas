package org.canvas.publication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.canvas.publication.asset.GeneratedAsset;

@Entity
@Table(name = "published_descriptions")
public class PublishedDescription {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "publication_id", nullable = false)
    private Publication publication;

    @Column(name = "approved_revision_id", nullable = false)
    private UUID approvedRevisionId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audio_asset_id")
    private GeneratedAsset audioAsset;

    protected PublishedDescription() {
    }

    PublishedDescription(Publication publication, UUID approvedRevisionId, int displayOrder,
            String label, String text) {
        this.publication = publication;
        this.approvedRevisionId = approvedRevisionId;
        this.displayOrder = displayOrder;
        this.label = label;
        this.text = text;
    }

    void associateAudioAsset(GeneratedAsset asset) {
        audioAsset = asset;
    }

    public UUID getId() { return id; }
    public UUID getApprovedRevisionId() { return approvedRevisionId; }
    public int getDisplayOrder() { return displayOrder; }
    public String getLabel() { return label; }
    public String getText() { return text; }
    public GeneratedAsset getAudioAsset() { return audioAsset; }
}
