-- Four-eyes rules compare the immutable subject of the employees, not their login ---------------
-- (an administrator can rename a login; the subject issued by the identity provider never changes).
ALTER TABLE customer
    ADD COLUMN registered_by_subject VARCHAR(64),
    ADD COLUMN verified_by_subject   VARCHAR(64),
    ADD COLUMN suspended_by          VARCHAR(100),
    ADD COLUMN suspended_by_subject  VARCHAR(64);

-- Files registered before this migration: the login stands in for the unknown subject.
UPDATE customer
SET registered_by_subject = registered_by,
    verified_by_subject   = verified_by;

ALTER TABLE customer ALTER COLUMN registered_by_subject SET NOT NULL;

ALTER TABLE customer DROP CONSTRAINT ck_customer_verification;
ALTER TABLE customer ADD CONSTRAINT ck_customer_verification
    CHECK (verified_by_subject IS NULL OR verified_by_subject <> registered_by_subject);

ALTER TABLE customer_aud
    ADD COLUMN registered_by_subject VARCHAR(64),
    ADD COLUMN verified_by_subject   VARCHAR(64),
    ADD COLUMN suspended_by          VARCHAR(100),
    ADD COLUMN suspended_by_subject  VARCHAR(64);
