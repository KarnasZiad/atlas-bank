package ma.atlasbank.notification.domain;

/**
 * E-mail is not a confidential channel: account numbers only appear masked.
 */
public final class AccountNumbers {

    private static final int VISIBLE_DIGITS = 4;
    private static final String MASK = "**** ";

    private AccountNumbers() {
    }

    public static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= VISIBLE_DIGITS) {
            return MASK;
        }
        return MASK + accountNumber.substring(accountNumber.length() - VISIBLE_DIGITS);
    }
}
