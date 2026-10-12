package ma.atlasbank.transaction.domain;

import java.util.Objects;
import java.util.StringJoiner;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.money.Money;

/**
 * The content of a payment order, validated for shape: which accounts a kind requires and that
 * money only moves between two distinct accounts. Funds, limits and ownership are the ledger's call.
 *
 * @param sourceAccountNumber debited account, absent for deposits
 * @param targetAccountNumber credited account, absent for withdrawals
 */
public record OrderRequest(OrderKind kind, String sourceAccountNumber, String targetAccountNumber,
                           Money amount, String description) {

    public OrderRequest {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new BusinessRuleViolationException(TransactionErrorCode.INVALID_AMOUNT, "Amount must be positive");
        }
        if (kind == OrderKind.TRANSFER && Objects.equals(sourceAccountNumber, targetAccountNumber)) {
            throw new BusinessRuleViolationException(TransactionErrorCode.SAME_ACCOUNT_TRANSFER,
                    "Source and target accounts must differ");
        }
    }

    public static OrderRequest transfer(String source, String target, Money amount, String description) {
        return new OrderRequest(OrderKind.TRANSFER, Objects.requireNonNull(source), Objects.requireNonNull(target),
                amount, description);
    }

    public static OrderRequest deposit(String target, Money amount, String description) {
        return new OrderRequest(OrderKind.DEPOSIT, null, Objects.requireNonNull(target), amount, description);
    }

    public static OrderRequest withdrawal(String source, Money amount, String description) {
        return new OrderRequest(OrderKind.WITHDRAWAL, Objects.requireNonNull(source), null, amount, description);
    }

    /**
     * Canonical text of the request, input of the idempotency fingerprint.
     */
    public String canonicalForm() {
        return new StringJoiner("|")
                .add(kind.name())
                .add(String.valueOf(sourceAccountNumber))
                .add(String.valueOf(targetAccountNumber))
                .add(amount.amount().toPlainString())
                .add(amount.currency().getCurrencyCode())
                .add(String.valueOf(description))
                .toString();
    }
}
