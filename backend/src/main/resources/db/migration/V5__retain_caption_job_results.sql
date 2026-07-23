ALTER TABLE caption_jobs DROP CONSTRAINT caption_jobs_result_check;

-- Retain successful terminal job ownership so generated drafts remain auditable and queryable.
ALTER TABLE caption_jobs ADD COLUMN retained_result_description_id UUID;

UPDATE caption_jobs
SET retained_result_description_id = result_description_id;

ALTER TABLE caption_jobs DROP COLUMN result_description_id;

ALTER TABLE caption_jobs RENAME COLUMN retained_result_description_id TO result_description_id;

ALTER TABLE caption_jobs
    ADD CONSTRAINT caption_jobs_result_description_id_fkey
    FOREIGN KEY (result_description_id) REFERENCES descriptions(id) ON DELETE RESTRICT;

ALTER TABLE caption_jobs
    ADD CONSTRAINT caption_jobs_result_check CHECK (
        (state = 'SUCCEEDED' AND result_description_id IS NOT NULL AND error_message IS NULL)
        OR (state = 'FAILED' AND result_description_id IS NULL AND error_message IS NOT NULL)
        OR (state IN ('PENDING', 'RUNNING') AND result_description_id IS NULL AND error_message IS NULL)
    );
