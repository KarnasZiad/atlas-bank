-- Who decided a manual freeze: lifting it takes another person ------------------------------------
ALTER TABLE account
    ADD COLUMN frozen_by         VARCHAR(100),
    ADD COLUMN frozen_by_subject VARCHAR(64);

ALTER TABLE account_aud
    ADD COLUMN frozen_by         VARCHAR(100),
    ADD COLUMN frozen_by_subject VARCHAR(64);
