package ma.atlasbank.account.domain;

public enum AccountType {
    CURRENT,
    SAVINGS,
    /**
     * Internal account of the bank: the cash held at the branch counter, one per currency. It is the
     * counterpart of every cash deposit and withdrawal, so that each posting is balanced (double entry)
     * and the till can be reconciled with the cash actually counted.
     */
    TELLER_CASH;

    /** Internal accounts belong to the bank, not to a customer, and are never operated through the API. */
    public boolean isInternal() {
        return this == TELLER_CASH;
    }
}
