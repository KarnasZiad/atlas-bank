-- Local projection of customer contact details (fed by customer snapshots) -------------------
CREATE TABLE recipient
(
    customer_id      UUID                     NOT NULL,
    full_name        VARCHAR(201)             NOT NULL,
    email            VARCHAR(254)             NOT NULL,
    phone_number     VARCHAR(20),
    language         VARCHAR(8)               NOT NULL,
    snapshot_version BIGINT                   NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_recipient PRIMARY KEY (customer_id)
);

-- Notifications sent ------------------------------------------------------------------------
CREATE TABLE notification
(
    id              UUID                     NOT NULL,
    customer_id     UUID                     NOT NULL,
    type            VARCHAR(40)              NOT NULL,
    channel         VARCHAR(20)              NOT NULL,
    source_event_id UUID                     NOT NULL,
    destination     VARCHAR(254)             NOT NULL,
    subject         VARCHAR(200)             NOT NULL,
    body            VARCHAR(4000)            NOT NULL,
    sent_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    -- One notification per event, customer and type: redelivered events are harmless.
    CONSTRAINT uk_notification_source UNIQUE (source_event_id, customer_id, type),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('EMAIL'))
);

CREATE INDEX ix_notification_customer ON notification (customer_id, sent_at DESC);
