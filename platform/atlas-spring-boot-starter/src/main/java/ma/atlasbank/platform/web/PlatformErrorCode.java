package ma.atlasbank.platform.web;

import ma.atlasbank.commons.error.ErrorCode;

/**
 * Error codes for technical failures that are common to every service.
 */
public enum PlatformErrorCode implements ErrorCode {
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    INVALID_SORT_PROPERTY,
    INVALID_AMOUNT,
    CURRENCY_MISMATCH,
    CONCURRENT_MODIFICATION,
    DUPLICATE_RESOURCE,
    ACCESS_DENIED,
    INVALID_TOKEN,
    INTERNAL_ERROR
}
