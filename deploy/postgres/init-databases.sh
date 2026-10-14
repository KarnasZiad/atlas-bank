#!/usr/bin/env bash
# Database-per-service, with two logins per service, created on the first start of the PostgreSQL
# container:
#   <service>_owner  owns the schema and runs the Flyway migrations (DDL);
#   <service>_svc    is what the running service connects with: data access only, granted through
#                    default privileges, and further restricted by the migrations (for instance the
#                    ledger journal is insert-only). A compromised service can neither alter the
#                    schema nor disable a trigger.
# Passwords come from the environment (see .env.example) and are passed as psql variables, never
# concatenated into SQL.
set -euo pipefail

create_database() {
  local database="$1" owner="$2" owner_password="$3" app="$4" app_password="$5"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v database="$database" -v owner="$owner" -v owner_password="$owner_password" \
    -v app="$app" -v app_password="$app_password" <<'SQL'
CREATE ROLE :"owner" LOGIN PASSWORD :'owner_password';
CREATE ROLE :"app" LOGIN PASSWORD :'app_password';
CREATE DATABASE :"database" OWNER :"owner";
REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"database" TO :"app";
SQL
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$database" \
    -v owner="$owner" -v app="$app" <<'SQL'
-- Only the owner creates objects; the service role reads and writes the data of the owner's tables.
ALTER SCHEMA public OWNER TO :"owner";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO :"app";
SQL
}

create_database customer_db customer_owner "${CUSTOMER_DB_OWNER_PASSWORD:?}" customer_svc "${CUSTOMER_DB_PASSWORD:?}"
create_database account_db account_owner "${ACCOUNT_DB_OWNER_PASSWORD:?}" account_svc "${ACCOUNT_DB_PASSWORD:?}"
create_database transaction_db transaction_owner "${TRANSACTION_DB_OWNER_PASSWORD:?}" transaction_svc "${TRANSACTION_DB_PASSWORD:?}"
create_database notification_db notification_owner "${NOTIFICATION_DB_OWNER_PASSWORD:?}" notification_svc "${NOTIFICATION_DB_PASSWORD:?}"

# Keycloak manages its own schema: a single owner login.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v password="${KEYCLOAK_DB_PASSWORD:?}" <<'SQL'
CREATE ROLE keycloak LOGIN PASSWORD :'password';
CREATE DATABASE keycloak OWNER keycloak;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
SQL
