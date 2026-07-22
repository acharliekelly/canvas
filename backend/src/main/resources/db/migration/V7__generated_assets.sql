CREATE TABLE generated_assets (
    id UUID PRIMARY KEY,
    kind VARCHAR(16) NOT NULL,
    input_key VARCHAR(64) NOT NULL,
    media_type VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    generator VARCHAR(64) NOT NULL,
    source_revision_id UUID,
    source_publication_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT generated_assets_kind_check CHECK (kind IN ('AUDIO', 'QR_CODE')),
    CONSTRAINT generated_assets_input_key_check CHECK (CHAR_LENGTH(input_key) = 64),
    CONSTRAINT generated_assets_byte_size_check CHECK (byte_size > 0),
    CONSTRAINT generated_assets_source_check CHECK (
        (kind = 'AUDIO' AND source_revision_id IS NOT NULL AND source_publication_id IS NULL)
        OR (kind = 'QR_CODE' AND source_revision_id IS NULL AND source_publication_id IS NOT NULL)
    ),
    CONSTRAINT generated_assets_kind_input_unique UNIQUE (kind, input_key),
    CONSTRAINT generated_assets_object_key_unique UNIQUE (object_key)
);

CREATE INDEX generated_assets_source_revision_idx ON generated_assets(source_revision_id);
CREATE INDEX generated_assets_source_publication_idx ON generated_assets(source_publication_id);
