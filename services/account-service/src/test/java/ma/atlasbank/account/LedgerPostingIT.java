package ma.atlasbank.account;

import static org.assertj.core.api.Assertions.assertThat;

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
import ma.atlasbank.contracts.payment.TransactionRequested.Initiator;
import ma.atlasbank.contracts.payment.TransactionRequested.InitiatorType;
import ma.atlasbank.testsupport.containers.InfrastructureContainers;
import ma.atlasbank.testsupport.kafka.TopicReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The ledger against real PostgreSQL and Kafka: money is never created, lost or moved twice.
 */
@SpringBootTest
@Import(InfrastructureContainers.class)
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
        UUID id = UUID.randomUUID();
        return new TransactionRequested(UUID.randomUUID(), Instant.now(), id, "TX-" + id.toString().substring(0, 8),
                kind, source, target, new BigDecimal(amount), "MAD", "Integration test", initiator);
    }
}
