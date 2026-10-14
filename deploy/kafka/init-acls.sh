#!/usr/bin/env bash
# Least privilege on Kafka: each service authenticates with its own principal and may only write the
# topics it owns and read the topics it consumes. In particular, only the transaction service can
# write payment orders and only the ledger can write posting outcomes.
#
# Runs once the broker is up (compose service "kafka-init"); adding an ACL that exists is a no-op.
# Keep in sync with the listeners of the services and with ma.atlasbank.contracts.Topics.
# (Read and Write imply Describe; each call starts a JVM, hence the grouping per principal.)
set -euo pipefail
source /opt/atlas/admin-client.sh
admin_client_config /tmp/admin.properties

CUSTOMER_SNAPSHOTS=atlas.customer.snapshots.v1
ACCOUNT_OPENED=atlas.account.opened.v1
ACCOUNT_STATUS_CHANGED=atlas.account.status-changed.v1
TRANSACTION_REQUESTED=atlas.payment.transaction-requested.v1
TRANSACTION_OUTCOMES=atlas.ledger.transaction-outcomes.v1

acl() {
  /opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9092 --command-config /tmp/admin.properties \
    --add --force "$@" > /dev/null
}

# Every service ensures the platform topology exists at startup (idempotent topic creation).
acl --allow-principal User:customer-service --allow-principal User:account-service \
    --allow-principal User:transaction-service --allow-principal User:notification-service \
    --operation Create --operation Describe --operation DescribeConfigs \
    --resource-pattern-type prefixed --topic atlas.

# Producers.
acl --allow-principal User:customer-service --operation Write --topic "$CUSTOMER_SNAPSHOTS"
acl --allow-principal User:transaction-service --operation Write --topic "$TRANSACTION_REQUESTED"
acl --allow-principal User:account-service --operation Write \
    --topic "$ACCOUNT_OPENED" --topic "$ACCOUNT_STATUS_CHANGED" --topic "$TRANSACTION_OUTCOMES"

# Consumers: the topics they read through their consumer groups (the @KafkaListener ids), and the
# dead-letter companions where they park the records that failed.
acl --allow-principal User:transaction-service --operation Read \
    --topic "$TRANSACTION_OUTCOMES" --group order-outcomes
acl --allow-principal User:transaction-service --operation Write --topic "$TRANSACTION_OUTCOMES.dlt"

acl --allow-principal User:account-service --operation Read \
    --topic "$TRANSACTION_REQUESTED" --topic "$CUSTOMER_SNAPSHOTS" --group ledger-postings --group account-holders
acl --allow-principal User:account-service --operation Write \
    --topic "$TRANSACTION_REQUESTED.dlt" --topic "$CUSTOMER_SNAPSHOTS.dlt"

acl --allow-principal User:notification-service --operation Read \
    --topic "$CUSTOMER_SNAPSHOTS" --topic "$ACCOUNT_OPENED" --topic "$ACCOUNT_STATUS_CHANGED" --topic "$TRANSACTION_OUTCOMES" \
    --group notification-recipients --group notification-account-opened --group notification-account-status \
    --group notification-transaction-outcomes
acl --allow-principal User:notification-service --operation Write \
    --topic "$CUSTOMER_SNAPSHOTS.dlt" --topic "$ACCOUNT_OPENED.dlt" --topic "$ACCOUNT_STATUS_CHANGED.dlt" \
    --topic "$TRANSACTION_OUTCOMES.dlt"

echo "Kafka ACLs in place."
