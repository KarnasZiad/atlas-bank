package ma.atlasbank.commons.money;

/**
 * The amount or currency of a {@link Money} is not acceptable (unknown currency, too many decimals...).
 */
public class InvalidMoneyException extends IllegalArgumentException {

    public InvalidMoneyException(String message) {
        super(message);
    }
}
