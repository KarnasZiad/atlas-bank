package ma.atlasbank.transaction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.money.Money;
import org.junit.jupiter.api.Test;

class PaymentOrderTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final Money HUNDRED = Money.of(new BigDecimal("100"), "MAD");

    @Test
    void startsPendingAndSettlesOnce() {
        PaymentOrder order = transfer();

        order.complete(NOW);
        order.complete(NOW.plusSeconds(1));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void refusesContradictingOutcomes() {
        PaymentOrder order = transfer();
        order.reject("INSUFFICIENT_FUNDS", NOW);

        assertThatThrownBy(() -> order.complete(NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(order.getRejectionCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void transfersNeedTwoDistinctAccounts() {
        assertThatThrownBy(() -> OrderRequest.transfer("MA64A", "MA64A", HUNDRED, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("differ");
    }

    @Test
    void amountsMustBePositive() {
        assertThatThrownBy(() -> OrderRequest.deposit("MA64A", Money.zero(HUNDRED.currency()), null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void canonicalFormIgnoresAmountFormatting() {
        OrderRequest plain = OrderRequest.transfer("A", "B", Money.of(new BigDecimal("100"), "MAD"), "rent");
        OrderRequest withDecimals = OrderRequest.transfer("A", "B", Money.of(new BigDecimal("100.00"), "MAD"), "rent");

        assertThat(plain.canonicalForm()).isEqualTo(withDecimals.canonicalForm());
    }

    private static PaymentOrder transfer() {
        return PaymentOrder.place(OrderRequest.transfer("A", "B", HUNDRED, "rent"), "TRF-20261012-ABCDEFGH",
                new Initiator("subject", "salma", UUID.randomUUID(), InitiatorType.CUSTOMER), "key-0123456789abcdef",
                "fingerprint", NOW);
    }
}
