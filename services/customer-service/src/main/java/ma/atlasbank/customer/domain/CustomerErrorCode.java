package ma.atlasbank.customer.domain;

import ma.atlasbank.commons.error.ErrorCode;

public enum CustomerErrorCode implements ErrorCode {
    CUSTOMER_NOT_FOUND,
    EMAIL_ALREADY_REGISTERED,
    NATIONAL_ID_ALREADY_REGISTERED,
    INVALID_NATIONAL_ID,
    INVALID_PHONE_NUMBER,
    INVALID_COUNTRY_CODE,
    UNSUPPORTED_LANGUAGE,
    CUSTOMER_UNDERAGE,
    INVALID_STATUS_TRANSITION,
    SELF_APPROVAL_FORBIDDEN,
    CUSTOMER_CLOSED
}
