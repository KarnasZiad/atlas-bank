-- Contact details of closed customers are erased from the projection ------------------------------
ALTER TABLE recipient
    ALTER COLUMN full_name DROP NOT NULL,
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN language DROP NOT NULL,
    ADD COLUMN erased_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE recipient ADD CONSTRAINT ck_recipient_contact
    CHECK (erased_at IS NOT NULL OR (full_name IS NOT NULL AND email IS NOT NULL AND language IS NOT NULL));
