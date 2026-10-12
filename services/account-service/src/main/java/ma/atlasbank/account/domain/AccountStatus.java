package ma.atlasbank.account.domain;

/**
 * {@code FROZEN} blocks debits only ("blocage au débit"): incoming funds are still accepted.
 * {@code CLOSED} is final and blocks every movement.
 */
public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
