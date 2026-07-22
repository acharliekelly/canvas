CREATE TABLE artworks (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    credit VARCHAR(255) NOT NULL,
    context TEXT,
    media_type VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    public_slug VARCHAR(255) UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT artworks_lifecycle_status_check CHECK (lifecycle_status = 'UPLOADED')
);
