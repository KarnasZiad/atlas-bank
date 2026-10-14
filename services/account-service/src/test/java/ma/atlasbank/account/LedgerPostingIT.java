package ma.atlasbank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import ma.atlasbank.account.application.AccountCommandService;
import ma.atlasbank.account.application.AccountHolderProjector;
import ma.atlasbank.account.application.AccountQueryService;
import ma.atlasbank.account.application.LedgerPostingService;
import ma.atlasbank.account.application.LedgerReconciliation;
import ma.atlasbank.account.application.view.ReconciliationReport;
import ma.atlasbank.account.application.command.OpenAccountCommand;
import ma.atlasbank.account.application.view.AccountView;
import ma.atlasbank.account.domain.AccountStatus;
import ma.atlasbank.account.domain.AccountType;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.commons.security.Role;
import ma.atlasbank.contracts.Topics;
import ma.atlasbank.contracts.customer.CustomerSnapshot;
import ma.atlasbank.contracts.customer.CustomerStatus;
import ma.atlasbank.contracts.ledger.TransactionOutcome;
import ma.atlasbank.contracts.ledger.TransactionOutcome.PostingResult;
import ma.atlasbank.contracts.payment.TransactionKind;
import ma.atlasbank.contracts.payment.TransactionRequested;
import ma.atlasbank.contracts.payment.TransactionRequested.Approval;
import ma.atlasbank.contracts.payment.TransactionRequested.Initiator;
import ma.atlasbank.contracts.payment.TransactionRequested.InitiatorType;
import ma.atlasbank.commons.security.UntrustedMessageException;
import ma.atlasbank.testsupport.containers.InfrastructureContainers;
import ma.atlasbank.testsupport.kafka.TopicReader;
import ma.atlasbank.testsupport.security.TestOrderSigning;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * The ledger against real PostgreSQL and Kafka: money is never created, lost or moved twice.
 */
@SpringBootTest
@Import(InfrastructureContainers.class)
@TestPropertySource(properties = {TestOrderSigning.TRUSTED_KEY_ID, TestOrderSigning.TRUSTED_PUBLIC_KEY})
class LedgerPostingIT {

    private static final Requester STAFF = new Requester("agent", "agent.test", null, Set.of(Role.EMPLOYEE));

    @Autowired
    private AccountHolderProjector holders;

    @Autowired
    private AccountCommandService accounts;

    @Autowired
    private AccountQueryService queries;

    @Autowired
    private LedgerPostingService ledger;

    @Autowired
    private LedgerReconciliation reconciliation;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KafkaTemplate<Object, Object> kafka;

    @Autowired
    private ConsumerFactory<?, ?> consumerFactory;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void postsDepositsAndTransfersWithMatchingLedgerEntries() {
        UUID alice = activeCustomer();
        AccountView aliceAccount = open(alice, AccountType.CURRENT);
        AccountView bobAccount = open(activeCustomer(), AccountType.CURRENT);

        ledger.post(deposit(aliceAccount, "1000.00"));
        ledger.post(transfer(alice, aliceAccount, bobAccount, "300.25"));

        assertThat(balanceOf(aliceAccount)).isEqualByComparingTo("699.75");
        assertThat(balanceOf(bobAccount)).isEqualByComparingTo("300.25");
        assertThat(queries.statement(aliceAccount.id(), null, null, STAFF, PageRequest.of(0, 10)).getContent())
                .extracting(entry -> entry.direction().name())
                .containsExactlyInAnyOrder("CREDIT", "DEBIT");
    }

    @Test
    void rejectsWithoutAnySideEffect() {
        UUID alice = activeCustomer();
        AccountView aliceAccount = open(alice, AccountType.CURRENT);
        AccountView bobAccount = open(activeCustomer(), AccountType.CURRENT);
        ledger.post(deposit(aliceAccount, "100.00"));

        TransactionRequested tooLarge = transfer(alice, aliceAccount, bobAccount, "5100.01");
        ledger.post(tooLarge);

        assertThat(balanceOf(aliceAccount)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(bobAccount)).isEqualByComparingTo("0");
        assertThat(receiptOf(tooLarge)).isEqualTo("REJECTED:INSUFFICIENT_FUNDS");
    }

    @Test
    void appliesARedeliveredOrderOnlyOnce() {
        UUID alice = activeCustomer();
        AccountView aliceAccount = open(alice, AccountType.CURRENT);
        AccountView bobAccount = open(activeCustomer(), AccountType.CURRENT);
        ledger.post(deposit(aliceAccount, "500.00"));

        TransactionRequested transfer = transfer(alice, aliceAccount, bobAccount, "200.00");
        ledger.post(transfer);
        ledger.post(transfer);

        assertThat(balanceOf(aliceAccount)).isEqualByComparingTo("300.00");
        assertThat(balanceOf(bobAccount)).isEqualByComparingTo("200.00");
    }

    @Test
    void refusesTransfersFromAnotherCustomersAccount() {
        AccountView victim = open(activeCustomer(), AccountType.CURRENT);
        AccountView thief = open(activeCustomer(), AccountType.CURRENT);
        ledger.post(deposit(victim, "1000.00"));

        TransactionRequested theft = transfer(thief.customerId(), victim, thief, "1000.00");
        ledger.post(theft);

        assertThat(balanceOf(victim)).isEqualByComparingTo("1000.00");
        assertThat(receiptOf(theft)).isEqualTo("REJECTED:SOURCE_ACCOUNT_NOT_OWNED");
    }

    @Test
    void doesNotRevealWhetherAnUnknownSourceAccountExists() {
        UUID customer = activeCustomer();
        AccountView own = open(customer, AccountType.CURRENT);
        AccountView ghost = new AccountView(UUID.randomUUID(), "MA64999001000009999999999999", customer,
                AccountType.CURRENT, "MAD", null, null, null, null, null, null, null, null);

        TransactionRequested probe = transfer(customer, ghost, own, "1.00");
        ledger.post(probe);

        assertThat(receiptOf(probe)).isEqualTo("REJECTED:SOURCE_ACCOUNT_NOT_OWNED");
    }

    @Test
    void enforcesTheDailyDebitLimitOfTheProduct() {
        AccountView savings = open(activeCustomer(), AccountType.SAVINGS);
        ledger.post(deposit(savings, "30000.00"));

        ledger.post(withdrawal(savings, "15000.00"));
        TransactionRequested overLimit = withdrawal(savings, "5000.01");
        ledger.post(overLimit);

        assertThat(balanceOf(savings)).isEqualByComparingTo("15000.00");
        assertThat(receiptOf(overLimit)).isEqualTo("REJECTED:DAILY_LIMIT_EXCEEDED");
    }

    @Test
    void concurrentOppositeTransfersNeitherDeadlockNorLoseMoney() throws Exception {
        UUID alice = activeCustomer();
        UUID bob = activeCustomer();
        AccountView aliceAccount = open(alice, AccountType.CURRENT);
        AccountView bobAccount = open(bob, AccountType.CURRENT);
        ledger.post(deposit(aliceAccount, "10000.00"));
        ledger.post(deposit(bobAccount, "10000.00"));

        List<Callable<Void>> transfers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            transfers.add(posting(transfer(alice, aliceAccount, bobAccount, "100.00")));
            transfers.add(posting(transfer(bob, bobAccount, aliceAccount, "50.00")));
        }
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            for (Future<Void> outcome : executor.invokeAll(transfers)) {
                outcome.get();
            }
        }

        assertThat(balanceOf(aliceAccount)).isEqualByComparingTo("9000.00");
        assertThat(balanceOf(bobAccount)).isEqualByComparingTo("11000.00");
    }

    @Test
    void suspendingACustomerFreezesTheirAccountsUntilReinstatement() {
        UUID customer = UUID.randomUUID();
        holders.apply(snapshot(customer, CustomerStatus.ACTIVE, 1));
        AccountView account = open(customer, AccountType.CURRENT);
        ledger.post(deposit(account, "100.00"));

        holders.apply(snapshot(customer, CustomerStatus.SUSPENDED, 2));
        assertThat(queries.get(account.id(), STAFF).status()).isEqualTo(AccountStatus.FROZEN);
        TransactionRequested blocked = withdrawal(account, "10.00");
        ledger.post(blocked);
        assertThat(receiptOf(blocked)).isEqualTo("REJECTED:ACCOUNT_FROZEN");

        holders.apply(snapshot(customer, CustomerStatus.SUSPENDED, 1));
        holders.apply(snapshot(customer, CustomerStatus.ACTIVE, 3));
        assertThat(queries.get(account.id(), STAFF).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void processesOrdersFromKafkaAndPublishesTheOutcome() {
        AccountView account = open(activeCustomer(), AccountType.CURRENT);
        TransactionRequested order = deposit(account, "42.00");

        kafka.send(Topics.TRANSACTION_REQUESTED, order.partitionKey(), jsonMapper.writeValueAsBytes(order));

        List<TransactionOutcome> outcomes = new TopicReader(consumerFactory, jsonMapper).await(
                Topics.TRANSACTION_OUTCOMES, TransactionOutcome.class,
                received -> received.stream().anyMatch(o -> o.transactionId().equals(order.transactionId())),
                Duration.ofSeconds(30));
        assertThat(outcomes)
                .filteredOn(outcome -> outcome.transactionId().equals(order.transactionId()))
                .singleElement()
                .satisfies(outcome -> {
                    assertThat(outcome.result()).isEqualTo(PostingResult.POSTED);
                    assertThat(outcome.credited().customerId()).isEqualTo(account.customerId());
                });
        assertThat(balanceOf(account)).isEqualByComparingTo("42.00");
    }

    @Test
    void neverPostsAnOrderNotSignedByTheTransactionService() {
        AccountView account = open(activeCustomer(), AccountType.CURRENT);
        TransactionRequested forged = unsigned(TransactionKind.DEPOSIT, null, account.accountNumber(), "1000000.00",
                "MAD", employee(), null);
        TransactionRequested genuine = deposit(account, "10.00");
        TransactionRequested inflated = withAmount(genuine, "10000.00");

        assertThatThrownBy(() -> ledger.post(forged)).isInstanceOf(UntrustedMessageException.class);
        assertThatThrownBy(() -> ledger.post(inflated)).isInstanceOf(UntrustedMessageException.class);

        assertThat(balanceOf(account)).isEqualByComparingTo("0");
        // Nothing is recorded for a forgery: the genuine order with the same identifier still goes through.
        assertThat(jdbc.queryForObject("select count(*) from posting_receipt where transaction_id = ?",
                Integer.class, genuine.transactionId())).isZero();
        ledger.post(genuine);
        assertThat(balanceOf(account)).isEqualByComparingTo("10.00");
    }

    @Test
    void sendsForgedOrdersFromKafkaToTheDeadLetterTopicWithoutEffect() {
        AccountView account = open(activeCustomer(), AccountType.CURRENT);
        TransactionRequested forged = unsigned(TransactionKind.DEPOSIT, null, account.accountNumber(), "500.00",
                "MAD", employee(), null);

        kafka.send(Topics.TRANSACTION_REQUESTED, forged.partitionKey(), jsonMapper.writeValueAsBytes(forged));

        new TopicReader(consumerFactory, jsonMapper).await(
                Topics.TRANSACTION_REQUESTED + ".dlt", TransactionRequested.class,
                received -> received.stream().anyMatch(order -> order.transactionId().equals(forged.transactionId())),
                Duration.ofSeconds(30));
        assertThat(balanceOf(account)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select count(*) from posting_receipt where transaction_id = ?",
                Integer.class, forged.transactionId())).isZero();
    }

    @Test
    void cashOperationsHaveTheTillAsCounterpart() {
        AccountView account = open(activeCustomer(), AccountType.CURRENT);
        BigDecimal tillBefore = tillBalance("MAD");

        TransactionRequested cashIn = deposit(account, "700.00");
        ledger.post(cashIn);
        TransactionRequested cashOut = withdrawal(account, "200.00");
        ledger.post(cashOut);

        assertThat(tillBalance("MAD")).isEqualByComparingTo(tillBefore.add(new BigDecimal("500.00")));
        assertThat(entriesOf(cashIn)).containsExactlyInAnyOrder("DEBIT:CASH-00100-MAD", "CREDIT:" + account.accountNumber());
        assertThat(entriesOf(cashOut)).containsExactlyInAnyOrder("DEBIT:" + account.accountNumber(), "CREDIT:CASH-00100-MAD");
        // The customer statement shows a cash movement, not the bank's internal account.
        assertThat(queries.statement(account.id(), null, null, STAFF, PageRequest.of(0, 10)).getContent())
                .allSatisfy(entry -> assertThat(entry.counterpartyAccountNumber()).isNull());
    }

    @Test
    void theTillNeverPaysOutCashItDoesNotHold() {
        // EUR cash is only ever withdrawn in this test: the EUR till is empty.
        UUID customer = activeCustomer();
        AccountView euroAccount = accounts.open(new OpenAccountCommand(customer, AccountType.CURRENT, "EUR"));
        TransactionRequested withdrawal = TestOrderSigning.sign(unsigned(TransactionKind.WITHDRAWAL,
                euroAccount.accountNumber(), null, "100.00", "EUR", employee(), null));

        ledger.post(withdrawal);

        assertThat(receiptOf(withdrawal)).isEqualTo("REJECTED:INSUFFICIENT_CASH");
        assertThat(balanceOf(euroAccount)).isEqualByComparingTo("0");
    }

    @Test
    void internalAccountsCannotBeNamedInAnOrder() {
        UUID customer = activeCustomer();
        AccountView own = open(customer, AccountType.CURRENT);
        ledger.post(deposit(own, "100.00"));
        AccountView till = new AccountView(UUID.randomUUID(), "CASH-00100-MAD", null, AccountType.TELLER_CASH, "MAD",
                null, null, null, null, null, null, null, null);

        TransactionRequested toTheTill = transfer(customer, own, till, "50.00");
        TransactionRequested fromTheTill = transfer(customer, till, own, "50.00");
        TransactionRequested depositIntoTheTill = deposit(till, "50.00");
        ledger.post(toTheTill);
        ledger.post(fromTheTill);
        ledger.post(depositIntoTheTill);

        assertThat(receiptOf(toTheTill)).isEqualTo("REJECTED:ACCOUNT_NOT_FOUND");
        assertThat(receiptOf(fromTheTill)).isEqualTo("REJECTED:SOURCE_ACCOUNT_NOT_OWNED");
        assertThat(receiptOf(depositIntoTheTill)).isEqualTo("REJECTED:ACCOUNT_NOT_FOUND");
        assertThat(balanceOf(own)).isEqualByComparingTo("100.00");
    }

    @Test
    void refusesAStaffOrderApprovedByTheEmployeeWhoPlacedIt() {
        AccountView account = open(activeCustomer(), AccountType.CURRENT);
        TransactionRequested selfApproved = TestOrderSigning.sign(unsigned(TransactionKind.DEPOSIT, null,
                account.accountNumber(), "20000.00", "MAD", employee(), new Approval("agent", "agent.test")));
        TransactionRequested approved = TestOrderSigning.sign(unsigned(TransactionKind.DEPOSIT, null,
                account.accountNumber(), "20000.00", "MAD", employee(), new Approval("checker", "agent.checker")));

        ledger.post(selfApproved);
        ledger.post(approved);

        assertThat(receiptOf(selfApproved)).isEqualTo("REJECTED:FOUR_EYES_VIOLATION");
        assertThat(receiptOf(approved)).isEqualTo("POSTED:");
        assertThat(balanceOf(account)).isEqualByComparingTo("20000.00");
    }

    @Test
    void keepsTheBooksBalancedAndDetectsAnyDiscrepancy() {
        UUID alice = activeCustomer();
        AccountView aliceAccount = open(alice, AccountType.CURRENT);
        AccountView bobAccount = open(activeCustomer(), AccountType.CURRENT);
        ledger.post(deposit(aliceAccount, "300.00"));
        ledger.post(transfer(alice, aliceAccount, bobAccount, "120.00"));
        ledger.post(withdrawal(bobAccount, "20.00"));

        assertThat(reconciliation.reconcile().isBalanced()).isTrue();

        jdbc.update("update account set balance = balance + 1 where id = ?", bobAccount.id());
        try {
            ReconciliationReport report = reconciliation.reconcile();
            assertThat(report.isBalanced()).isFalse();
            assertThat(report.accountsNotMatchingTheirEntries()).containsExactly(bobAccount.accountNumber());
        } finally {
            jdbc.update("update account set balance = balance - 1 where id = ?", bobAccount.id());
        }
    }

    @Test
    void theJournalAndTheReceiptsAreAppendOnly() {
        AccountView account = open(activeCustomer(), AccountType.CURRENT);
        TransactionRequested order = deposit(account, "1.00");
        ledger.post(order);

        assertThatThrownBy(() -> jdbc.update("delete from posting_receipt where transaction_id = ?", order.transactionId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("truncate ledger_entry"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("update ledger_entry set amount = 2 where transaction_id = ?", order.transactionId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
    }

    private List<String> entriesOf(TransactionRequested order) {
        return jdbc.queryForList("""
                select e.direction || ':' || a.account_number from ledger_entry e join account a on a.id = e.account_id
                where e.transaction_id = ?""", String.class, order.transactionId());
    }

    private Callable<Void> posting(TransactionRequested order) {
        return () -> {
            ledger.post(order);
            return null;
        };
    }

    private UUID activeCustomer() {
        UUID customerId = UUID.randomUUID();
        holders.apply(snapshot(customerId, CustomerStatus.ACTIVE, 1));
        return customerId;
    }

    private AccountView open(UUID customerId, AccountType type) {
        return accounts.open(new OpenAccountCommand(customerId, type, "MAD"));
    }

    private BigDecimal balanceOf(AccountView account) {
        return queries.get(account.id(), STAFF).balance();
    }

    private String receiptOf(TransactionRequested order) {
        return jdbc.queryForObject("select result || ':' || coalesce(rejection_code, '') from posting_receipt where transaction_id = ?",
                String.class, order.transactionId());
    }

    private static CustomerSnapshot snapshot(UUID customerId, CustomerStatus status, long version) {
        return new CustomerSnapshot(UUID.randomUUID(), Instant.now(), customerId, version, "Test", "Customer",
                customerId + "@example.ma", "+212600000000", "fr", status);
    }

    private static TransactionRequested deposit(AccountView target, String amount) {
        return order(TransactionKind.DEPOSIT, null, target.accountNumber(), amount, employee());
    }

    private static TransactionRequested withdrawal(AccountView source, String amount) {
        return order(TransactionKind.WITHDRAWAL, source.accountNumber(), null, amount, employee());
    }

    private static TransactionRequested transfer(UUID customerId, AccountView source, AccountView target, String amount) {
        return order(TransactionKind.TRANSFER, source.accountNumber(), target.accountNumber(), amount,
                new Initiator("subject-" + customerId, "customer", customerId, InitiatorType.CUSTOMER));
    }

    private static Initiator employee() {
        return new Initiator("agent", "agent.test", null, InitiatorType.EMPLOYEE);
    }

    private static TransactionRequested order(TransactionKind kind, String source, String target, String amount,
                                              Initiator initiator) {
        return TestOrderSigning.sign(unsigned(kind, source, target, amount, "MAD", initiator, null));
    }

    private static TransactionRequested unsigned(TransactionKind kind, String source, String target, String amount,
                                                 String currency, Initiator initiator, Approval approval) {
        UUID id = UUID.randomUUID();
        return new TransactionRequested(UUID.randomUUID(), Instant.now(), id, "TX-" + id.toString().substring(0, 8),
                kind, source, target, new BigDecimal(amount), currency, "Integration test", initiator, approval, null);
    }

    private static TransactionRequested withAmount(TransactionRequested order, String amount) {
        return new TransactionRequested(order.eventId(), order.occurredAt(), order.transactionId(), order.reference(),
                order.kind(), order.sourceAccountNumber(), order.targetAccountNumber(), new BigDecimal(amount),
                order.currency(), order.description(), order.initiator(), order.approval(), order.signature());
    }

    private BigDecimal tillBalance(String currency) {
        return jdbc.queryForObject("select coalesce(max(balance), 0) from account where account_type = 'TELLER_CASH' and currency = ?",
                BigDecimal.class, currency);
    }
}
