package ma.atlasbank.notification.domain;

/**
 * Each type has a subject and a body template per language (see {@code messages/notifications*.properties}).
 */
public enum NotificationType {
    ACCOUNT_OPENED,
    ACCOUNT_STATUS_CHANGED,
    TRANSFER_SENT,
    TRANSFER_RECEIVED,
    DEPOSIT_RECEIVED,
    WITHDRAWAL_MADE,
    TRANSACTION_REJECTED,
    CONTACT_DETAILS_CHANGED
}
