ALTER TABLE description_revisions
    ADD CONSTRAINT description_revisions_owner_id_unique
    UNIQUE (description_id, id);

ALTER TABLE descriptions
    ADD COLUMN current_revision_owner_id UUID;

UPDATE descriptions
SET current_revision_owner_id = id
WHERE current_revision_id IS NOT NULL;

ALTER TABLE descriptions
    ADD CONSTRAINT descriptions_current_revision_owner_check
    CHECK (current_revision_id IS NULL
        OR (current_revision_owner_id IS NOT NULL AND current_revision_owner_id = id));

ALTER TABLE descriptions
    ADD CONSTRAINT descriptions_current_revision_owner_fk
    FOREIGN KEY (current_revision_owner_id, current_revision_id)
    REFERENCES description_revisions(description_id, id)
    ON DELETE SET NULL;

ALTER TABLE description_revisions
    ADD CONSTRAINT description_revisions_parent_owner_fk
    FOREIGN KEY (description_id, parent_revision_id)
    REFERENCES description_revisions(description_id, id);
