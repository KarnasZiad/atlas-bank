#!/usr/bin/env bash
# Database-per-service: one database and one dedicated login per service, created on the first
# start of the PostgreSQL container. Passwords come from the environment (see .env.example) and
# are passed as psql variables, never concatenated into SQL.
set -euo pipefail

create_database() {
  local database="$1" owner="$2" password="$3"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v database="$database" -v owner="$owner" -v password="$password" <<'SQL'
CREATE ROLE :"owner" LOGIN PASSWORD :'password';
CREATE DATABASE :"database" OWNER :"owner";
REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
SQL
}

create_database customer_db customer_svc "${CUSTOMER_DB_PASSWORD:?}"
create_database account_db account_svc "${ACCOUNT_DB_PASSWORD:?}"
create_database transaction_db transaction_svc "${TRANSACTION_DB_PASSWORD:?}"
create_database notification_db notification_svc "${NOTIFICATION_DB_PASSWORD:?}"
create_database keycloak keycloak "${KEYCLOAK_DB_PASSWORD:?}"
