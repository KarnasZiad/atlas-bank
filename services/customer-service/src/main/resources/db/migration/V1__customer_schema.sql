-- Customer aggregate ------------------------------------------------------------------------
CREATE TABLE customer
(
    id                      UUID                     NOT NULL,
    first_name              VARCHAR(100)             NOT NULL,
    last_name               VARCHAR(100)             NOT NULL,
    email                   VARCHAR(254)             NOT NULL,
    phone_number            VARCHAR(20)              NOT NULL,
    date_of_birth           DATE                     NOT NULL,
    national_id             VARCHAR(512)             NOT NULL, -- AES-256-GCM ciphertext
    national_id_fingerprint VARCHAR(64)              NOT NULL, -- HMAC-SHA256 blind index
    address_line            VARCHAR(200)             NOT NULL,
    address_city            VARCHAR(100)             NOT NULL,
    address_postal_code     VARCHAR(20)              NOT NULL,
    address_country         VARCHAR(2)               NOT NULL,
    preferred_language      VARCHAR(8)               NOT NULL,
    status                  VARCHAR(30)              NOT NULL,
    status_reason           VARCHAR(500),
    registered_by           VARCHAR(100)             NOT NULL,
    verified_by             VARCHAR(100),
    verified_at             TIMESTAMP WITH TIME ZONE,
    identity_user_id        VARCHAR(64),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by              VARCHAR(100)             NOT NULL,
    last_modified_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by        VARCHAR(100)             NOT NULL,
    version                 BIGINT                   NOT NULL,
    CONSTRAINT pk_customer PRIMARY KEY (id),
    CONSTRAINT uk_customer_email UNIQUE (email),
    CONSTRAINT uk_customer_national_id_fingerprint UNIQUE (national_id_fingerprint),
    CONSTRAINT ck_customer_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_customer_verification CHECK (verified_by IS NULL OR verified_by <> registered_by)
);

CREATE INDEX ix_customer_status ON customer (status);
CREATE INDEX ix_customer_last_name ON customer (last_name);

-- Audit trail (Hibernate Envers) ------------------------------------------------------------
CREATE SEQUENCE audit_revision_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE audit_revision
(
    id                 BIGINT       NOT NULL,
    revision_timestamp BIGINT       NOT NULL,
    actor              VARCHAR(100) NOT NULL,
    CONSTRAINT pk_audit_revision PRIMARY KEY (id)
);

CREATE TABLE customer_aud
(
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL,
    revtype             SMALLINT,
    first_name          VARCHAR(100),
    last_name           VARCHAR(100),
    email               VARCHAR(254),
    phone_number        VARCHAR(20),
    date_of_birth       DATE,
    national_id         VARCHAR(512),
    address_line        VARCHAR(200),
    address_city        VARCHAR(100),
    address_postal_code VARCHAR(20),
    address_country     VARCHAR(2),
    preferred_language  VARCHAR(8),
    status              VARCHAR(30),
    status_reason       VARCHAR(500),
    registered_by       VARCHAR(100),
    verified_by         VARCHAR(100),
    verified_at         TIMESTAMP WITH TIME ZONE,
    identity_user_id    VARCHAR(64),
    CONSTRAINT pk_customer_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_customer_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
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
