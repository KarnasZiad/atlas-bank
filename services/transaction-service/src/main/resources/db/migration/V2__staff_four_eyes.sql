-- Four-eyes control of staff orders ------------------------------------------------------------
-- An employee may place orders alone up to configured limits; above them, the order waits for a
-- second employee and only reaches the ledger once approved.
ALTER TABLE payment_order
    ADD COLUMN approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reviewer_subject  VARCHAR(64),
    ADD COLUMN reviewer_username VARCHAR(100),
    ADD COLUMN reviewed_at       TIMESTAMP WITH TIME ZONE;

ALTER TABLE payment_order ALTER COLUMN approval_required DROP DEFAULT;

ALTER TABLE payment_order DROP CONSTRAINT ck_payment_order_status;
ALTER TABLE payment_order ADD CONSTRAINT ck_payment_order_status
    CHECK (status IN ('AWAITING_APPROVAL', 'PENDING', 'COMPLETED', 'REJECTED'));

-- Last line of defence: an order that needed a review can only move forward when someone other
-- than its initiator approved it.
ALTER TABLE payment_order ADD CONSTRAINT ck_payment_order_four_eyes CHECK (
    NOT approval_required
        OR status IN ('AWAITING_APPROVAL', 'REJECTED')
        OR (reviewer_subject IS NOT NULL AND reviewer_subject <> initiator_subject));

-- What an employee placed alone today (limits without approval).
CREATE INDEX ix_payment_order_initiator_day ON payment_order (initiator_subject, requested_at);
