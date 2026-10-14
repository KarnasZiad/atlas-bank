package ma.atlasbank.transaction.domain;

import ma.atlasbank.commons.error.ErrorCode;

public enum TransactionErrorCode implements ErrorCode {
    ORDER_NOT_FOUND,
    IDEMPOTENCY_KEY_REUSED,
    INVALID_ACCOUNT_NUMBER,
    SAME_ACCOUNT_TRANSFER,
    UNSUPPORTED_CURRENCY,
    AMOUNT_ABOVE_LIMIT,
    INVALID_AMOUNT,
    INVALID_PERIOD,
    ORDER_NOT_AWAITING_APPROVAL,
    SELF_APPROVAL_FORBIDDEN,
    /** Rejection code of a staff order refused by the reviewing employee. */
    REFUSED_BY_REVIEWER,
    /** Rejection code of a staff order nobody reviewed before the end of its business day. */
    APPROVAL_EXPIRED
}
