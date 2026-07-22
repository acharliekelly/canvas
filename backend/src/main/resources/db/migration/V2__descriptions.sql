CREATE TABLE descriptions (
    id UUID PRIMARY KEY,
    artwork_id UUID NOT NULL REFERENCES artworks(id) ON DELETE CASCADE,
    source VARCHAR(32) NOT NULL,
    display_order INTEGER NOT NULL,
    current_revision_id UUID UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT descriptions_source_check CHECK (source IN ('MANUAL', 'GENERATED')),
    CONSTRAINT descriptions_display_order_check CHECK (display_order >= 0),
    CONSTRAINT descriptions_artwork_display_order_unique UNIQUE (artwork_id, display_order)
);

CREATE TABLE description_revisions (
    id UUID PRIMARY KEY,
    description_id UUID NOT NULL REFERENCES descriptions(id) ON DELETE CASCADE,
    label VARCHAR(255) NOT NULL,
    text TEXT NOT NULL,
    state VARCHAR(32) NOT NULL,
    parent_revision_id UUID REFERENCES description_revisions(id),
    approved_by VARCHAR(255),
    approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT description_revisions_state_check CHECK (state IN ('DRAFT', 'APPROVED')),
    CONSTRAINT description_revisions_approval_check CHECK (
        (state = 'DRAFT' AND approved_by IS NULL AND approved_at IS NULL)
        OR (state = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
    )
);

ALTER TABLE descriptions
    ADD CONSTRAINT descriptions_current_revision_fk
    FOREIGN KEY (current_revision_id) REFERENCES description_revisions(id) ON DELETE SET NULL;

CREATE INDEX descriptions_artwork_id_idx ON descriptions(artwork_id);
CREATE INDEX description_revisions_description_id_idx ON description_revisions(description_id);
