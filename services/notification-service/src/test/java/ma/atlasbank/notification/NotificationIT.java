package ma.atlasbank.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import ma.atlasbank.contracts.IntegrationEvent;
import ma.atlasbank.contracts.Topics;
import ma.atlasbank.contracts.customer.ContactDetailsChanged;
import ma.atlasbank.contracts.customer.ContactDetailsChanged.ContactField;
import ma.atlasbank.contracts.customer.CustomerSnapshot;
import ma.atlasbank.contracts.customer.CustomerStatus;
import ma.atlasbank.contracts.ledger.TransactionOutcome;
import ma.atlasbank.contracts.ledger.TransactionOutcome.Party;
import ma.atlasbank.contracts.ledger.TransactionOutcome.PostingResult;
import ma.atlasbank.contracts.payment.TransactionKind;
import ma.atlasbank.notification.application.port.MessageSender;
import ma.atlasbank.notification.domain.RenderedMessage;
import ma.atlasbank.testsupport.containers.InfrastructureContainers;
import ma.atlasbank.testsupport.security.TestTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Events in, localized messages out: delivered once per event and recipient, even when the
 * contact details arrive after the event or the event is redelivered.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InfrastructureContainers.class)
class NotificationIT {

    private static final Duration DELIVERY = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<Object, Object> kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private MessageSender sender;

    @Test
    void notifiesBothSidesOfATransferInTheirOwnLanguage() {
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();
        publish(Topics.CUSTOMER_SNAPSHOTS, snapshot(payer, "payer@example.ma", "fr"));
        publish(Topics.CUSTOMER_SNAPSHOTS, snapshot(payee, "payee@example.ma", "en"));

        TransactionOutcome transfer = postedTransfer(payer, payee);
        publish(Topics.TRANSACTION_OUTCOMES, transfer);

        ArgumentCaptor<RenderedMessage> toPayer = ArgumentCaptor.forClass(RenderedMessage.class);
        verify(sender, timeout(DELIVERY.toMillis())).send(eq("payer@example.ma"), toPayer.capture());
        assertThat(toPayer.getValue().subject()).startsWith("Virement de");
        assertThat(toPayer.getValue().body()).contains("**** ").doesNotContain(transfer.debited().accountNumber());

        ArgumentCaptor<RenderedMessage> toPayee = ArgumentCaptor.forClass(RenderedMessage.class);
        verify(sender, timeout(DELIVERY.toMillis())).send(eq("payee@example.ma"), toPayee.capture());
        assertThat(toPayee.getValue().subject()).startsWith("You received");
    }

    @Test
    void ignoresRedeliveredEvents() {
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();
        publish(Topics.CUSTOMER_SNAPSHOTS, snapshot(payer, payer + "@example.ma", "fr"));
        publish(Topics.CUSTOMER_SNAPSHOTS, snapshot(payee, payee + "@example.ma", "fr"));

        TransactionOutcome transfer = postedTransfer(payer, payee);
        publish(Topics.TRANSACTION_OUTCOMES, transfer);
        verify(sender, timeout(DELIVERY.toMillis())).send(eq(payee + "@example.ma"), any());
        publish(Topics.TRANSACTION_OUTCOMES, transfer);

        verify(sender, after(3_000).times(1)).send(eq(payer + "@example.ma"), any());
        verify(sender, times(1)).send(eq(payee + "@example.ma"), any());
    }

    @Test
    void waitsForContactDetailsThatArriveAfterTheEvent() {
        UUID customer = UUID.randomUUID();
        TransactionOutcome rejected = new TransactionOutcome(UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
                "TRF-20261012-LATE0001", TransactionKind.TRANSFER, PostingResult.REJECTED, "INSUFFICIENT_FUNDS",
                new BigDecimal("75.00"), "MAD", new Party("MA640000000000000000000000001", customer),
                new Party("MA640000000000000000000000002", UUID.randomUUID()), customer);

        publish(Topics.TRANSACTION_OUTCOMES, rejected);
        publish(Topics.CUSTOMER_SNAPSHOTS, snapshot(customer, "late@example.ma", "fr"));

        ArgumentCaptor<RenderedMessage> message = ArgumentCaptor.forClass(RenderedMessage.class);
        verify(sender, timeout(DELIVERY.toMillis())).send(eq("late@example.ma"), message.capture());
        assertThat(message.getValue().body()).contains("provision insuffisante");
    }

    @Test
    void customersReadOnlyTheirOwnNotifications() throws Exception {
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();
        publish(Topics.CUSTOMER_SNAPSHOTS, snapshot(payer, payer + "@example.ma", "fr"));
        publish(Topics.CUSTOMER_SNAPSHOTS, snapshot(payee, payee + "@example.ma", "en"));
        publish(Topics.TRANSACTION_OUTCOMES, postedTransfer(payer, payee));
        verify(sender, timeout(DELIVERY.toMillis())).send(eq(payee + "@example.ma"), any());

        await().atMost(DELIVERY).untilAsserted(() ->
                mvc.perform(get("/api/v1/notifications").with(TestTokens.customer(payer)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content.length()").value(1))
                        .andExpect(jsonPath("$.content[0].type").value("TRANSFER_SENT"))
                        .andExpect(jsonPath("$.content[0].customerId").value(payer.toString())));
    }

    @Test
    void warnsTheCustomerWhenContactDetailsChange() {
        UUID customer = UUID.randomUUID();
        publish(Topics.CUSTOMER_SNAPSHOTS, snapshot(customer, "watch@example.ma", "fr"));
        publish(Topics.CUSTOMER_CONTACT_CHANGED, new ContactDetailsChanged(UUID.randomUUID(), Instant.now(), customer,
                Set.of(ContactField.PHONE_NUMBER, ContactField.POSTAL_ADDRESS), true));

        ArgumentCaptor<RenderedMessage> warning = ArgumentCaptor.forClass(RenderedMessage.class);
        verify(sender, timeout(DELIVERY.toMillis())).send(eq("watch@example.ma"), warning.capture());
        assertThat(warning.getValue().subject()).isEqualTo("Modification de vos coordonnées");
        assertThat(warning.getValue().body())
                .contains("numéro de téléphone et adresse postale")
                .contains("depuis votre espace en ligne")
                .doesNotContain("+212");
    }

    @Test
    void erasesTheContactDetailsOfClosedCustomersAndWritesToThemNoMore() {
        UUID customer = UUID.randomUUID();
        publish(Topics.CUSTOMER_SNAPSHOTS, snapshot(customer, "leaving@example.ma", "fr"));
        publish(Topics.CUSTOMER_SNAPSHOTS, new CustomerSnapshot(UUID.randomUUID(), Instant.now(), customer, 2,
                null, null, null, null, null, CustomerStatus.CLOSED));

        await().atMost(DELIVERY).untilAsserted(() -> assertThat(jdbc.queryForMap(
                "select email, full_name, erased_at from recipient where customer_id = ?", customer))
                .containsEntry("email", null).containsEntry("full_name", null)
                .extractingByKey("erased_at").isNotNull());

        publish(Topics.TRANSACTION_OUTCOMES, postedTransfer(customer, UUID.randomUUID()));
        verify(sender, after(3_000).never()).send(eq("leaving@example.ma"), any());
    }

    private void publish(String topic, IntegrationEvent event) {
        kafka.send(topic, event.partitionKey(), jsonMapper.writeValueAsBytes(event));
    }

    private static CustomerSnapshot snapshot(UUID customerId, String email, String language) {
        return new CustomerSnapshot(UUID.randomUUID(), Instant.now(), customerId, 1, "Salma", "Benali", email,
                "+212612345678", language, CustomerStatus.ACTIVE);
    }

    private static TransactionOutcome postedTransfer(UUID payer, UUID payee) {
        return new TransactionOutcome(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), "TRF-20261012-ABCDEFGH",
                TransactionKind.TRANSFER, PostingResult.POSTED, null, new BigDecimal("250.00"), "MAD",
                new Party("MA640000000000000000000000011", payer), new Party("MA640000000000000000000000022", payee),
                payer);
    }
}
