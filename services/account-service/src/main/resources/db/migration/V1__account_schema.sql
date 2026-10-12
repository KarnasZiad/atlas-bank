-- Accounts ----------------------------------------------------------------------------------
CREATE SEQUENCE account_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE account
(
    id                UUID                     NOT NULL,
    account_number    VARCHAR(34)              NOT NULL,
    customer_id       UUID                     NOT NULL,
    account_type      VARCHAR(20)              NOT NULL,
    currency          VARCHAR(3)               NOT NULL,
    balance           NUMERIC(19, 4)           NOT NULL,
    overdraft_limit   NUMERIC(19, 4)           NOT NULL,
    daily_debit_limit NUMERIC(19, 4)           NOT NULL,
    status            VARCHAR(20)              NOT NULL,
    freeze_cause      VARCHAR(30),
    status_reason     VARCHAR(500),
    opened_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at         TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by        VARCHAR(100)             NOT NULL,
    last_modified_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by  VARCHAR(100)             NOT NULL,
    version           BIGINT                   NOT NULL,
    CONSTRAINT pk_account PRIMARY KEY (id),
    CONSTRAINT uk_account_number UNIQUE (account_number),
    CONSTRAINT ck_account_type CHECK (account_type IN ('CURRENT', 'SAVINGS')),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_account_limits CHECK (overdraft_limit >= 0 AND daily_debit_limit > 0),
    -- Last line of defence: whatever the application does, a balance never exceeds its overdraft.
    CONSTRAINT ck_account_balance_within_overdraft CHECK (balance >= -overdraft_limit)
);

CREATE INDEX ix_account_customer ON account (customer_id);

-- Ledger: append-only journal of every movement ----------------------------------------------
CREATE TABLE ledger_entry
(
    id                          UUID                     NOT NULL,
    account_id                  UUID                     NOT NULL,
    transaction_id              UUID                     NOT NULL,
    transaction_reference       VARCHAR(40)              NOT NULL,
    transaction_kind            VARCHAR(20)              NOT NULL,
    direction                   VARCHAR(10)              NOT NULL,
    amount                      NUMERIC(19, 4)           NOT NULL,
    currency                    VARCHAR(3)               NOT NULL,
    balance_after               NUMERIC(19, 4)           NOT NULL,
    counterparty_account_number VARCHAR(34),
    description                 VARCHAR(140),
    booked_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entry_account FOREIGN KEY (account_id) REFERENCES account (id),
    -- A payment order moves each account at most once, even if it is delivered twice.
    CONSTRAINT uk_ledger_entry_transaction_account UNIQUE (transaction_id, account_id),
    CONSTRAINT ck_ledger_entry_amount CHECK (amount > 0),
    CONSTRAINT ck_ledger_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entry_kind CHECK (transaction_kind IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER'))
);

CREATE INDEX ix_ledger_entry_statement ON ledger_entry (account_id, booked_at DESC);

CREATE FUNCTION forbid_ledger_entry_change() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'Ledger entries are immutable: post a compensating entry instead';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entry_is_append_only
    BEFORE UPDATE OR DELETE
    ON ledger_entry
    FOR EACH ROW
EXECUTE FUNCTION forbid_ledger_entry_change();

-- Idempotent consumer: one receipt per processed payment order -------------------------------
CREATE TABLE posting_receipt
(
    transaction_id UUID                     NOT NULL,
    result         VARCHAR(10)              NOT NULL,
    rejection_code VARCHAR(60),
    processed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_posting_receipt PRIMARY KEY (transaction_id),
    CONSTRAINT ck_posting_receipt_result CHECK (result IN ('POSTED', 'REJECTED')),
    CONSTRAINT ck_posting_receipt_reason CHECK ((result = 'REJECTED') = (rejection_code IS NOT NULL))
);

-- Local projection of customers (fed by customer snapshots) ----------------------------------
CREATE TABLE account_holder
(
    customer_id      UUID                     NOT NULL,
    status           VARCHAR(30)              NOT NULL,
    snapshot_version BIGINT                   NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_account_holder PRIMARY KEY (customer_id)
);

-- Audit trail (Hibernate Envers) ------------------------------------------------------------
CREATE SEQUENCE audit_revision_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE audit_revision
(
    id                 BIGINT       NOT NULL,
    revision_timestamp BIGINT       NOT NULL,
    actor              VARCHAR(100) NOT NULL,
    CONSTRAINT pk_audit_revision PRIMARY KEY (id)
);

CREATE TABLE account_aud
(
    id                UUID     NOT NULL,
    rev               BIGINT   NOT NULL,
    revtype           SMALLINT,
    account_number    VARCHAR(34),
    customer_id       UUID,
    account_type      VARCHAR(20),
    currency          VARCHAR(3),
    overdraft_limit   NUMERIC(19, 4),
    daily_debit_limit NUMERIC(19, 4),
    status            VARCHAR(20),
    freeze_cause      VARCHAR(30),
    status_reason     VARCHAR(500),
    opened_at         TIMESTAMP WITH TIME ZONE,
    closed_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_account_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_account_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);

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
