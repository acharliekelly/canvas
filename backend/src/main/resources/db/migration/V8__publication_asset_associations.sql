-- A-to-B-to-A must create a new publication audit event, even when the historical content matches.
ALTER TABLE publications DROP CONSTRAINT publications_artwork_content_unique;

ALTER TABLE publications ADD COLUMN qr_asset_id UUID;
ALTER TABLE published_descriptions ADD COLUMN audio_asset_id UUID;

-- Backfill audio only for an unambiguous single candidate; ambiguous legacy rows stay nullable for text-first public compatibility.
UPDATE published_descriptions pd
SET audio_asset_id = (
    SELECT ga.id
    FROM generated_assets ga
    WHERE ga.kind = 'AUDIO'
      AND ga.source_revision_id = pd.approved_revision_id
)
WHERE (
    SELECT COUNT(*)
    FROM generated_assets ga
    WHERE ga.kind = 'AUDIO'
      AND ga.source_revision_id = pd.approved_revision_id
) = 1;

-- Backfill QR assets only for an unambiguous single candidate; ambiguous legacy rows stay nullable for text-first public compatibility.
UPDATE publications p
SET qr_asset_id = (
    SELECT ga.id
    FROM generated_assets ga
    WHERE ga.kind = 'QR_CODE'
      AND ga.source_publication_id = p.id
)
WHERE (
    SELECT COUNT(*)
    FROM generated_assets ga
    WHERE ga.kind = 'QR_CODE'
      AND ga.source_publication_id = p.id
) = 1;

-- ON DELETE RESTRICT protects QR assets referenced by publication snapshots.
ALTER TABLE publications
    ADD CONSTRAINT publications_qr_asset_id_fkey
    FOREIGN KEY (qr_asset_id) REFERENCES generated_assets(id) ON DELETE RESTRICT;

-- ON DELETE RESTRICT protects audio assets referenced by publication snapshots.
ALTER TABLE published_descriptions
    ADD CONSTRAINT published_descriptions_audio_asset_id_fkey
    FOREIGN KEY (audio_asset_id) REFERENCES generated_assets(id) ON DELETE RESTRICT;

CREATE INDEX publications_qr_asset_id_idx ON publications(qr_asset_id);
CREATE INDEX published_descriptions_audio_asset_id_idx ON published_descriptions(audio_asset_id);
