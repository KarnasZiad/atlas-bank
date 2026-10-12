package ma.atlasbank.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.atlasbank.contracts.Topics;
import ma.atlasbank.contracts.ledger.TransactionOutcome;
import ma.atlasbank.contracts.ledger.TransactionOutcome.Party;
import ma.atlasbank.contracts.ledger.TransactionOutcome.PostingResult;
import ma.atlasbank.contracts.payment.TransactionKind;
import ma.atlasbank.contracts.payment.TransactionRequested;
import ma.atlasbank.contracts.payment.TransactionRequested.InitiatorType;
import ma.atlasbank.testsupport.containers.InfrastructureContainers;
import ma.atlasbank.testsupport.kafka.TopicReader;
import ma.atlasbank.testsupport.security.TestTokens;
import org.iban4j.CountryCode;
import org.iban4j.Iban;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Order intake end to end: idempotency, validation, outbox publication and settlement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InfrastructureContainers.class)
class PaymentOrderIT {

    private static final String TRANSACTIONS = "/api/v1/transactions";
    private static final String SOURCE = iban(1);
    private static final String TARGET = iban(2);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private KafkaTemplate<Object, Object> kafka;

    @Autowired
    private ConsumerFactory<?, ?> consumerFactory;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void acceptsATransferAndHandsItToTheLedgerThroughTheOutbox() throws Exception {
        UUID customerId = UUID.randomUUID();
        String response = transfer(TestTokens.customer(customerId), key(), "250.00")
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("TRF-\\d{8}-[0-9A-Z]{8}")))
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(JsonPath.read(response, "$.id"));

        List<TransactionRequested> published = new TopicReader(consumerFactory, jsonMapper).await(
                Topics.TRANSACTION_REQUESTED, TransactionRequested.class,
                received -> received.stream().anyMatch(order -> order.transactionId().equals(orderId)),
                Duration.ofSeconds(30));
        assertThat(published)
                .filteredOn(order -> order.transactionId().equals(orderId))
                .singleElement()
                .satisfies(order -> {
                    assertThat(order.kind()).isEqualTo(TransactionKind.TRANSFER);
                    assertThat(order.amount()).isEqualByComparingTo("250.00");
                    assertThat(order.initiator().type()).isEqualTo(InitiatorType.CUSTOMER);
                    assertThat(order.initiator().customerId()).isEqualTo(customerId);
                });
    }

    @Test
    void replaysARetriedRequestInsteadOfPayingTwice() throws Exception {
        RequestPostProcessor customer = TestTokens.customer(UUID.randomUUID());
        String key = key();

        String first = transfer(customer, key, "99.90").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String retry = transfer(customer, key, "99.90")
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(retry, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
    }

    @Test
    void refusesAKeyReusedForADifferentRequest() throws Exception {
        RequestPostProcessor customer = TestTokens.customer(UUID.randomUUID());
        String key = key();
        transfer(customer, key, "10.00").andExpect(status().isAccepted());

        transfer(customer, key, "10000.00")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void requiresAnIdempotencyKey() throws Exception {
        mvc.perform(post(TRANSACTIONS + "/transfers").with(TestTokens.customer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(transferBody(SOURCE, TARGET, "10.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesAccountNumbersAndCeilings() throws Exception {
        RequestPostProcessor customer = TestTokens.customer(UUID.randomUUID());

        mvc.perform(post(TRANSACTIONS + "/transfers").with(customer).header("Idempotency-Key", key())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody("MA00000000000000000000000000", TARGET, "10.00")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_NUMBER"));

        transfer(customer, key(), "100000.01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AMOUNT_ABOVE_LIMIT"));
    }

    @Test
    void keepsCashOperationsForTellers() throws Exception {
        String deposit = """
                {"accountNumber": "%s", "amount": 500.00, "currency": "MAD"}
                """.formatted(TARGET);

        mvc.perform(post(TRANSACTIONS + "/deposits").with(TestTokens.customer(UUID.randomUUID()))
                        .header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content(deposit))
                .andExpect(status().isForbidden());
        mvc.perform(post(TRANSACTIONS + "/deposits").with(TestTokens.employee())
                        .header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content(deposit))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.kind").value("DEPOSIT"))
                .andExpect(jsonPath("$.initiatorType").value("EMPLOYEE"));
    }

    @Test
    void settlesTheOrderWithTheLedgerOutcome() throws Exception {
        UUID customerId = UUID.randomUUID();
        RequestPostProcessor customer = TestTokens.customer(customerId);
        String response = transfer(customer, key(), "42.00").andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(JsonPath.read(response, "$.id"));

        TransactionOutcome rejected = new TransactionOutcome(UUID.randomUUID(), Instant.now(), orderId,
                JsonPath.read(response, "$.reference"), TransactionKind.TRANSFER, PostingResult.REJECTED,
                "INSUFFICIENT_FUNDS", new BigDecimal("42.00"), "MAD", new Party(SOURCE, customerId),
                new Party(TARGET, null), customerId);
        kafka.send(Topics.TRANSACTION_OUTCOMES, rejected.partitionKey(), jsonMapper.writeValueAsBytes(rejected));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mvc.perform(get(TRANSACTIONS + "/{id}", orderId).with(customer))
                        .andExpect(jsonPath("$.status").value("REJECTED"))
                        .andExpect(jsonPath("$.rejectionCode").value("INSUFFICIENT_FUNDS")));
    }

    @Test
    void customersOnlySeeTheirOwnOrders() throws Exception {
        RequestPostProcessor owner = TestTokens.customer(UUID.randomUUID());
        String response = transfer(owner, key(), "5.00").andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(response, "$.id");

        mvc.perform(get(TRANSACTIONS + "/{id}", orderId).with(TestTokens.customer(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(get(TRANSACTIONS).with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    private ResultActions transfer(RequestPostProcessor token, String key, String amount) throws Exception {
        return mvc.perform(post(TRANSACTIONS + "/transfers").with(token).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(transferBody(SOURCE, TARGET, amount)));
    }

    private static String transferBody(String source, String target, String amount) {
        return """
                {"sourceAccountNumber": "%s", "targetAccountNumber": "%s", "amount": %s, "currency": "MAD",
                 "description": "Rent"}
                """.formatted(source, target, amount);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static String iban(int sequence) {
        return new Iban.Builder().countryCode(CountryCode.MA).bankCode("999").branchCode("00100")
                .accountNumber("%016d".formatted(sequence)).build().toString();
    }
}
