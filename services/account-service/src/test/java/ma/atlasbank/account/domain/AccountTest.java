package ma.atlasbank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.error.DomainException;
import ma.atlasbank.commons.error.OperationNotPermittedException;
import ma.atlasbank.commons.money.Money;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static final Currency MAD = Currency.getInstance("MAD");
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final Money NOTHING_TODAY = Money.zero(MAD);

    @Test
    void creditIncreasesTheBalanceAndRecordsTheResultingBalance() {
        Account account = account(BigDecimal.ZERO);

        LedgerEntry entry = account.credit(mad("150.50"), reference(TransactionKind.DEPOSIT), null, NOW);

        assertThat(account.balance()).isEqualTo(mad("150.50"));
        assertThat(entry.getDirection()).isEqualTo(EntryDirection.CREDIT);
        assertThat(entry.getBalanceAfter()).isEqualByComparingTo("150.50");
    }

    @Test
    void debitMayUseTheOverdraftButNotBeyond() {
        Account account = account(new BigDecimal("1000"));
        account.credit(mad("100"), reference(TransactionKind.DEPOSIT), null, NOW);

        account.debit(mad("1100"), reference(TransactionKind.WITHDRAWAL), NOTHING_TODAY, null, NOW);
        assertThat(account.balance()).isEqualTo(mad("-1000"));

        assertRejected(() -> account.debit(mad("0.01"), reference(TransactionKind.WITHDRAWAL), NOTHING_TODAY, null, NOW),
                AccountErrorCode.INSUFFICIENT_FUNDS);
    }

    @Test
    void debitRespectsTheDailyLimit() {
        Account account = account(BigDecimal.ZERO);
        account.credit(mad("50000"), reference(TransactionKind.DEPOSIT), null, NOW);

        assertRejected(() -> account.debit(mad("1500"), reference(TransactionKind.TRANSFER), mad("9000"), "X", NOW),
                AccountErrorCode.DAILY_LIMIT_EXCEEDED);
        assertThat(account.balance()).isEqualTo(mad("50000"));
    }

    @Test
    void rejectsForeignCurrenciesAndNonPositiveAmounts() {
        Account account = account(BigDecimal.ZERO);

        assertRejected(() -> account.credit(Money.of(BigDecimal.TEN, "EUR"), reference(TransactionKind.DEPOSIT), null, NOW),
                AccountErrorCode.CURRENCY_MISMATCH);
        assertRejected(() -> account.credit(mad("0"), reference(TransactionKind.DEPOSIT), null, NOW),
                AccountErrorCode.INVALID_AMOUNT);
    }

    @Test
    void frozenAccountsRefuseDebitsButAcceptCredits() {
        Account account = account(BigDecimal.ZERO);
        account.freeze(FreezeCause.MANUAL, "Investigation");

        assertRejected(() -> account.debit(mad("1"), reference(TransactionKind.WITHDRAWAL), NOTHING_TODAY, null, NOW),
                AccountErrorCode.ACCOUNT_FROZEN);
        account.credit(mad("10"), reference(TransactionKind.DEPOSIT), null, NOW);
        assertThat(account.balance()).isEqualTo(mad("10"));
    }

    @Test
    void aManualFreezeIsLiftedByAnotherPerson() {
        StaffMember investigator = new StaffMember("sub-amina", "agent.amina");
        Account account = account(BigDecimal.ZERO);
        account.freeze(FreezeCause.MANUAL, "Investigation", investigator);

        assertThatThrownBy(() -> account.unfreeze(new StaffMember("sub-amina", "agent.amina.renamed")))
                .isInstanceOf(OperationNotPermittedException.class)
                .extracting(error -> ((DomainException) error).errorCode())
                .isEqualTo(AccountErrorCode.SELF_RELEASE_FORBIDDEN);
        assertThat(account.getFrozenBy()).isEqualTo("agent.amina");

        account.unfreeze(new StaffMember("sub-admin", "admin.atlas"));
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getFrozenBy()).isNull();
    }

    @Test
    void liftingASuspensionNeverReleasesAManualFreeze() {
        Account manuallyFrozen = account(BigDecimal.ZERO);
        manuallyFrozen.freeze(FreezeCause.MANUAL, "Investigation");
        manuallyFrozen.freeze(FreezeCause.CUSTOMER_SUSPENDED, "Customer suspended");

        assertThat(manuallyFrozen.releaseCustomerSuspension()).isFalse();
        assertThat(manuallyFrozen.getStatus()).isEqualTo(AccountStatus.FROZEN);

        Account frozenBySuspension = account(BigDecimal.ZERO);
        frozenBySuspension.freeze(FreezeCause.CUSTOMER_SUSPENDED, "Customer suspended");

        assertThat(frozenBySuspension.releaseCustomerSuspension()).isTrue();
        assertThat(frozenBySuspension.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void onlyEmptyAccountsCanBeClosedAndClosureIsFinal() {
        Account account = account(BigDecimal.ZERO);
        account.credit(mad("1"), reference(TransactionKind.DEPOSIT), null, NOW);

        assertRejected(() -> account.close("Customer request", NOW), AccountErrorCode.BALANCE_NOT_ZERO);

        account.debit(mad("1"), reference(TransactionKind.WITHDRAWAL), NOTHING_TODAY, null, NOW);
        account.close("Customer request", NOW);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertRejected(() -> account.credit(mad("1"), reference(TransactionKind.DEPOSIT), null, NOW),
                AccountErrorCode.ACCOUNT_CLOSED);
    }

    @Test
    void theTillHoldsTheCashTakenInAndPaysOutNoMore() {
        Account till = Account.openTellerCash("CASH-00100-MAD", MAD, NOW);

        LedgerEntry cashIn = till.debit(mad("300"), reference(TransactionKind.DEPOSIT), NOTHING_TODAY, "MA64", NOW);
        till.credit(mad("100"), reference(TransactionKind.WITHDRAWAL), "MA64", NOW);

        assertThat(cashIn.getDirection()).isEqualTo(EntryDirection.DEBIT);
        assertThat(cashIn.getBalanceAfter()).isEqualByComparingTo("300");
        assertThat(till.balance()).isEqualTo(mad("200"));
        assertRejected(() -> till.credit(mad("200.01"), reference(TransactionKind.WITHDRAWAL), "MA64", NOW),
                AccountErrorCode.INSUFFICIENT_CASH);
        assertThat(till.balance()).isEqualTo(mad("200"));
    }

    @Test
    void theTillIsNeitherOwnedByACustomerNorOperatedLikeOne() {
        Account till = Account.openTellerCash("CASH-00100-MAD", MAD, NOW);

        assertThat(till.isInternal()).isTrue();
        assertThat(till.isOwnedBy(null)).isFalse();
        assertRejected(() -> till.freeze(FreezeCause.MANUAL, "test"), AccountErrorCode.INTERNAL_ACCOUNT);
        assertRejected(() -> till.close("test", NOW), AccountErrorCode.INTERNAL_ACCOUNT);
    }

    @Test
    void holderProjectionIgnoresStaleSnapshots() {
        AccountHolder holder = AccountHolder.of(UUID.randomUUID(), HolderStatus.ACTIVE, 3, NOW);

        assertThat(holder.apply(HolderStatus.SUSPENDED, 2, NOW)).isFalse();
        assertThat(holder.getStatus()).isEqualTo(HolderStatus.ACTIVE);
        assertThat(holder.apply(HolderStatus.SUSPENDED, 4, NOW)).isTrue();
        assertThat(holder.isBlocked()).isTrue();
    }

    private static void assertRejected(Runnable operation, AccountErrorCode expected) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(error -> ((DomainException) error).errorCode())
                .isEqualTo(expected);
    }

    private static Account account(BigDecimal overdraft) {
        return Account.open("MA64999001000000000000000001", UUID.randomUUID(), AccountType.CURRENT, MAD,
                new AccountLimits(overdraft, new BigDecimal("10000")), NOW);
    }

    private static TransactionReference reference(TransactionKind kind) {
        return new TransactionReference(UUID.randomUUID(), "TX-TEST", kind, "test");
    }

    private static Money mad(String amount) {
        return Money.of(new BigDecimal(amount), MAD);
    }
}
