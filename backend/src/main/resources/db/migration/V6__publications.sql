ALTER TABLE artworks DROP CONSTRAINT artworks_lifecycle_status_check;

ALTER TABLE artworks
    ADD CONSTRAINT artworks_lifecycle_status_check
    CHECK (lifecycle_status IN ('UPLOADED', 'PUBLISHED'));

CREATE TABLE publications (
    id UUID PRIMARY KEY,
    artwork_id UUID NOT NULL REFERENCES artworks(id) ON DELETE CASCADE,
    publication_version INTEGER NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    current_artwork_id UUID UNIQUE,
    snapshot_title VARCHAR(255) NOT NULL,
    snapshot_credit VARCHAR(255) NOT NULL,
    snapshot_image_object_key VARCHAR(512) NOT NULL,
    snapshot_image_media_type VARCHAR(64) NOT NULL,
    snapshot_image_byte_size BIGINT NOT NULL,
    published_by UUID NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT publications_version_check CHECK (publication_version > 0),
    CONSTRAINT publications_content_hash_check CHECK (CHAR_LENGTH(content_hash) = 64),
    CONSTRAINT publications_current_artwork_check CHECK (
        current_artwork_id IS NULL OR current_artwork_id = artwork_id
    ),
    CONSTRAINT publications_artwork_version_unique UNIQUE (artwork_id, publication_version),
    CONSTRAINT publications_artwork_content_unique UNIQUE (artwork_id, content_hash)
);

CREATE INDEX publications_artwork_id_idx ON publications(artwork_id);

CREATE TABLE published_descriptions (
    id UUID PRIMARY KEY,
    publication_id UUID NOT NULL REFERENCES publications(id) ON DELETE CASCADE,
    approved_revision_id UUID NOT NULL REFERENCES description_revisions(id) ON DELETE RESTRICT,
    display_order INTEGER NOT NULL,
    label VARCHAR(255) NOT NULL,
    text TEXT NOT NULL,
    CONSTRAINT published_descriptions_order_check CHECK (display_order >= 0),
    CONSTRAINT published_descriptions_publication_order_unique UNIQUE (publication_id, display_order),
    CONSTRAINT published_descriptions_publication_revision_unique UNIQUE (publication_id, approved_revision_id)
);

CREATE INDEX published_descriptions_publication_id_idx ON published_descriptions(publication_id);
