#!/usr/bin/env bash
# Writes the client configuration of the Kafka administrator (healthcheck, ACL setup) from the
# environment, so that the password never appears in a command line or in the image.
set -euo pipefail

admin_client_config() {
  local file="$1"
  umask 077
  cat > "$file" <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="admin" password="${ATLAS_KAFKA_ADMIN_PASSWORD:?}";
EOF
}
