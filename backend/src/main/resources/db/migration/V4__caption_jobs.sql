CREATE TABLE caption_jobs (
    id UUID PRIMARY KEY,
    artwork_id UUID NOT NULL REFERENCES artworks(id) ON DELETE CASCADE,
    active_artwork_id UUID UNIQUE,
    state VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL,
    error_message VARCHAR(512),
    result_description_id UUID REFERENCES descriptions(id) ON DELETE SET NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT caption_jobs_state_check CHECK (state IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT caption_jobs_attempt_count_check CHECK (attempt_count > 0),
    CONSTRAINT caption_jobs_active_check CHECK (
        (state IN ('PENDING', 'RUNNING') AND active_artwork_id = artwork_id)
        OR (state IN ('SUCCEEDED', 'FAILED') AND active_artwork_id IS NULL)
    ),
    CONSTRAINT caption_jobs_result_check CHECK (
        (state = 'SUCCEEDED' AND result_description_id IS NOT NULL AND error_message IS NULL)
        OR (state = 'FAILED' AND result_description_id IS NULL AND error_message IS NOT NULL)
        OR (state IN ('PENDING', 'RUNNING') AND result_description_id IS NULL AND error_message IS NULL)
    )
);

CREATE INDEX caption_jobs_artwork_id_idx ON caption_jobs(artwork_id);
