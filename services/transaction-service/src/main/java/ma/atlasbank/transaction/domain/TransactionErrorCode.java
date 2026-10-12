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
    INVALID_PERIOD
}
