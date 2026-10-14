-- Teller cash: internal accounts of the bank ---------------------------------------------------
-- The counterpart of every cash deposit and withdrawal, so that each posting debits and credits
-- the same amount (double entry). One per currency, owned by no customer, never named in an order.
ALTER TABLE account ALTER COLUMN customer_id DROP NOT NULL;

ALTER TABLE account DROP CONSTRAINT ck_account_type;
ALTER TABLE account ADD CONSTRAINT ck_account_type CHECK (account_type IN ('CURRENT', 'SAVINGS', 'TELLER_CASH'));

ALTER TABLE account ADD CONSTRAINT ck_account_owner CHECK ((account_type = 'TELLER_CASH') = (customer_id IS NULL));

-- Daily debit limits protect customer accounts; the till has none, and no overdraft: it can never
-- pay out more cash than it holds (its balance is the cash at the counter, see ck_account_balance_within_overdraft).
ALTER TABLE account DROP CONSTRAINT ck_account_limits;
ALTER TABLE account ADD CONSTRAINT ck_account_limits CHECK (
    (account_type <> 'TELLER_CASH' AND overdraft_limit >= 0 AND daily_debit_limit > 0)
        OR (account_type = 'TELLER_CASH' AND overdraft_limit = 0 AND daily_debit_limit = 0));

-- Append-only journal, whatever the statement ---------------------------------------------------
CREATE FUNCTION forbid_append_only_change() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION '% is append-only: post a compensating entry instead', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

-- Row triggers do not fire on TRUNCATE.
CREATE TRIGGER ledger_entry_is_not_truncated
    BEFORE TRUNCATE
    ON ledger_entry
    FOR EACH STATEMENT
EXECUTE FUNCTION forbid_append_only_change();

-- Posting receipts are the idempotency record of the ledger: deleting one would let a redelivered
-- order move money a second time.
CREATE TRIGGER posting_receipt_is_append_only
    BEFORE UPDATE OR DELETE
    ON posting_receipt
    FOR EACH ROW
EXECUTE FUNCTION forbid_append_only_change();

CREATE TRIGGER posting_receipt_is_not_truncated
    BEFORE TRUNCATE
    ON posting_receipt
    FOR EACH STATEMENT
EXECUTE FUNCTION forbid_append_only_change();

-- Least privilege of the runtime role -------------------------------------------------------------
-- Migrations run as the owner of the schema; the application connects with a separate role that
-- receives read/write access through default privileges (see deploy/postgres/init-databases.sh).
-- The journal and the receipts are insert-only for that role, so even a compromised application
-- cannot rewrite history nor disable the triggers above (only the owner can). Nothing to do when
-- migrations and runtime share one role (tests, single-user databases).
DO
$$
    DECLARE
        runtime_role TEXT := '${runtime-role}';
    BEGIN
        IF runtime_role <> '' AND runtime_role <> current_user
            AND EXISTS (SELECT FROM pg_roles WHERE rolname = runtime_role) THEN
            EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON ledger_entry, posting_receipt FROM %I', runtime_role);
            EXECUTE format('REVOKE ALL ON flyway_schema_history FROM %I', runtime_role);
        END IF;
    END
$$;
