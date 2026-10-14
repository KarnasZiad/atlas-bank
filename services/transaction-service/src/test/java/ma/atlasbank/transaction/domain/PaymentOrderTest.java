package ma.atlasbank.transaction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.error.OperationNotPermittedException;
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

    @Test
    void holdsAStaffOrderUntilAnotherEmployeeApprovesIt() {
        PaymentOrder order = staffDeposit();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_APPROVAL);

        assertThatThrownBy(() -> order.approve(new Reviewer("agent-1", "agent.amina"), NOW))
                .isInstanceOf(OperationNotPermittedException.class)
                .hasMessageContaining("another employee");
        assertThatThrownBy(() -> order.complete(NOW)).isInstanceOf(IllegalStateException.class);

        order.approve(new Reviewer("agent-2", "agent.youssef"), NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.approver()).hasValueSatisfying(approver -> assertThat(approver.getSubject()).isEqualTo("agent-2"));
    }

    @Test
    void aRefusedOrderIsFinalAndCarriesNoApproval() {
        PaymentOrder order = staffDeposit();

        order.refuse(new Reviewer("agent-1", "agent.amina"), NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionCode()).isEqualTo("REFUSED_BY_REVIEWER");
        assertThat(order.approver()).isEmpty();
        assertThatThrownBy(() -> order.approve(new Reviewer("agent-2", "agent.youssef"), NOW))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void ordersPlacedWithinTheLimitsNeedNoReview() {
        PaymentOrder order = transfer();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.approver()).isEmpty();
        assertThatThrownBy(() -> order.approve(new Reviewer("agent-2", "agent.youssef"), NOW))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    private static PaymentOrder staffDeposit() {
        return PaymentOrder.place(OrderRequest.deposit("A", HUNDRED, null), "DEP-20261012-ABCDEFGH",
                new Initiator("agent-1", "agent.amina", null, InitiatorType.EMPLOYEE), "key-0123456789abcdef",
                "fingerprint", NOW, true);
    }

    private static PaymentOrder transfer() {
        return PaymentOrder.place(OrderRequest.transfer("A", "B", HUNDRED, "rent"), "TRF-20261012-ABCDEFGH",
                new Initiator("subject", "salma", UUID.randomUUID(), InitiatorType.CUSTOMER), "key-0123456789abcdef",
                "fingerprint", NOW, false);
    }
}
