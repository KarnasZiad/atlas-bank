package ma.atlasbank.account.domain;

/**
 * Why an account is frozen, so that lifting a customer suspension only releases the accounts it
 * froze and never a freeze decided by an employee.
 */
public enum FreezeCause {
    MANUAL,
    CUSTOMER_SUSPENDED
}
