package ma.atlasbank.account.domain;

import ma.atlasbank.commons.error.ErrorCode;

/**
 * Stable reasons, exposed to API clients and used as rejection codes of ledger postings.
 */
public enum AccountErrorCode implements ErrorCode {
    ACCOUNT_NOT_FOUND,
    INVALID_PERIOD,
    ACCOUNT_FROZEN,
    ACCOUNT_CLOSED,
    INVALID_STATUS_TRANSITION,
    BALANCE_NOT_ZERO,
    CUSTOMER_NOT_ELIGIBLE,
    UNSUPPORTED_CURRENCY,
    CURRENCY_MISMATCH,
    INVALID_AMOUNT,
    MALFORMED_ORDER,
    INSUFFICIENT_FUNDS,
    DAILY_LIMIT_EXCEEDED,
    SAME_ACCOUNT_TRANSFER,
    SOURCE_ACCOUNT_NOT_OWNED,
    OPERATION_NOT_ALLOWED_FOR_INITIATOR
}
