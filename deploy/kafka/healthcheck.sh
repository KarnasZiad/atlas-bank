#!/usr/bin/env bash
# Broker readiness, checked with an authenticated client (anonymous access is disabled).
set -euo pipefail
source /opt/atlas/admin-client.sh
admin_client_config /tmp/healthcheck.properties
/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 \
  --command-config /tmp/healthcheck.properties > /dev/null
