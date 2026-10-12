-- Payment orders ----------------------------------------------------------------------------
CREATE TABLE payment_order
(
    id                    UUID                     NOT NULL,
    reference             VARCHAR(40)              NOT NULL,
    kind                  VARCHAR(20)              NOT NULL,
    status                VARCHAR(20)              NOT NULL,
    source_account_number VARCHAR(34),
    target_account_number VARCHAR(34),
    amount                NUMERIC(19, 4)           NOT NULL,
    currency              VARCHAR(3)               NOT NULL,
    description           VARCHAR(140),
    initiator_subject     VARCHAR(64)              NOT NULL,
    initiator_username    VARCHAR(100)             NOT NULL,
    initiator_customer_id UUID,
    initiator_type        VARCHAR(20)              NOT NULL,
    idempotency_key       VARCHAR(64)              NOT NULL,
    request_fingerprint   VARCHAR(64)              NOT NULL,
    rejection_code        VARCHAR(60),
    requested_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at          TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by            VARCHAR(100)             NOT NULL,
    last_modified_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by      VARCHAR(100)             NOT NULL,
    version               BIGINT                   NOT NULL,
    CONSTRAINT pk_payment_order PRIMARY KEY (id),
    CONSTRAINT uk_payment_order_reference UNIQUE (reference),
    -- Idempotency is scoped to the initiator: two users may pick the same key.
    CONSTRAINT uk_payment_order_idempotency UNIQUE (initiator_subject, idempotency_key),
    CONSTRAINT ck_payment_order_kind CHECK (kind IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT ck_payment_order_status CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED')),
    CONSTRAINT ck_payment_order_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_order_accounts CHECK (
        (kind = 'DEPOSIT' AND source_account_number IS NULL AND target_account_number IS NOT NULL)
            OR (kind = 'WITHDRAWAL' AND source_account_number IS NOT NULL AND target_account_number IS NULL)
            OR (kind = 'TRANSFER' AND source_account_number IS NOT NULL AND target_account_number IS NOT NULL
            AND source_account_number <> target_account_number)),
    CONSTRAINT ck_payment_order_initiator CHECK (initiator_type IN ('CUSTOMER', 'EMPLOYEE'))
);

CREATE INDEX ix_payment_order_customer ON payment_order (initiator_customer_id, requested_at DESC);
CREATE INDEX ix_payment_order_source ON payment_order (source_account_number);
CREATE INDEX ix_payment_order_target ON payment_order (target_account_number);
CREATE INDEX ix_payment_order_status ON payment_order (status);

-- Transactional outbox (Spring Modulith event publication registry, schema v2) -------------
CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
