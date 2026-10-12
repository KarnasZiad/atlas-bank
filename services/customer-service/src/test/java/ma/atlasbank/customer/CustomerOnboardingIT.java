package ma.atlasbank.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import ma.atlasbank.contracts.Topics;
import ma.atlasbank.contracts.customer.CustomerSnapshot;
import ma.atlasbank.contracts.customer.CustomerStatus;
import ma.atlasbank.customer.application.port.IdentityProvider;
import ma.atlasbank.testsupport.containers.InfrastructureContainers;
import ma.atlasbank.testsupport.kafka.TopicReader;
import ma.atlasbank.testsupport.security.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Full onboarding against real PostgreSQL and Kafka: maker-checker verification, asynchronous
 * identity provisioning, snapshots externalized through the outbox, encryption at rest and audit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InfrastructureContainers.class)
@TestPropertySource(properties = {
        "atlas.customers.personal-data.encryption-password=integration-test-password",
        "atlas.customers.personal-data.encryption-salt=0123456789abcdef",
        "atlas.customers.personal-data.fingerprint-key=integration-test-fingerprint-key-0123456789"
})
class CustomerOnboardingIT {

    private static final String CUSTOMERS = "/api/v1/customers";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConsumerFactory<?, ?> consumerFactory;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private IdentityProvider identityProvider;

    @Test
    void onboardsACustomerEndToEnd() throws Exception {
        given(identityProvider.provisionCustomerAccount(any())).willReturn("keycloak-user-1");
        UUID customerId = register("salma.benali@example.ma", "AB123456");

        mvc.perform(post(CUSTOMERS + "/{id}/verification", customerId).with(TestTokens.employee("agent.amina")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_FORBIDDEN"));

        mvc.perform(post(CUSTOMERS + "/{id}/verification", customerId).with(TestTokens.employee("agent.youssef")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.verifiedBy").value("agent.youssef"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                mvc.perform(get(CUSTOMERS + "/{id}", customerId).with(TestTokens.employee()))
                        .andExpect(jsonPath("$.identityProvisioned").value(true)));
        verify(identityProvider).provisionCustomerAccount(argThat(account -> account.customerId().equals(customerId)));

        List<CustomerSnapshot> snapshots = new TopicReader(consumerFactory, jsonMapper)
                .await(Topics.CUSTOMER_SNAPSHOTS, CustomerSnapshot.class,
                        received -> received.stream().filter(s -> s.customerId().equals(customerId)).count() >= 2,
                        Duration.ofSeconds(30));
        assertThat(snapshots)
                .filteredOn(snapshot -> snapshot.customerId().equals(customerId))
                .extracting(CustomerSnapshot::status)
                .containsExactly(CustomerStatus.PENDING_VERIFICATION, CustomerStatus.ACTIVE);
        assertThat(snapshots)
                .filteredOn(snapshot -> snapshot.customerId().equals(customerId))
                .extracting(CustomerSnapshot::version)
                .isSorted();

        String storedNationalId = jdbc.queryForObject(
                "select national_id from customer where id = ?", String.class, customerId);
        assertThat(storedNationalId).doesNotContain("AB123456").hasSizeGreaterThan(40);

        mvc.perform(get(CUSTOMERS + "/{id}/history", customerId).with(TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[2].changeType").value("INSERT"))
                .andExpect(jsonPath("$.content[2].changedBy").value("agent.amina"))
                .andExpect(jsonPath("$.content[1].changedBy").value("agent.youssef"));
    }

    @Test
    void detectsDuplicateNationalIdsDespiteEncryption() throws Exception {
        register("first.holder@example.ma", "CD765432");

        mvc.perform(post(CUSTOMERS).with(TestTokens.employee())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("second.holder@example.ma", "cd765432")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NATIONAL_ID_ALREADY_REGISTERED"));
    }

    @Test
    void customersOnlySeeTheirOwnFile() throws Exception {
        UUID customerId = register("owner@example.ma", "EF112233");

        mvc.perform(get(CUSTOMERS + "/{id}", customerId).with(TestTokens.customer(customerId)))
                .andExpect(status().isOk());
        mvc.perform(get(CUSTOMERS + "/{id}", customerId).with(TestTokens.customer(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    private UUID register(String email, String nationalId) throws Exception {
        String response = mvc.perform(post(CUSTOMERS).with(TestTokens.employee("agent.amina"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email, nationalId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private static String registration(String email, String nationalId) {
        return """
                {
                  "firstName": "Salma",
                  "lastName": "Benali",
                  "email": "%s",
                  "phoneNumber": "0612345678",
                  "dateOfBirth": "1990-05-17",
                  "nationalId": "%s",
                  "preferredLanguage": "fr",
                  "address": {"line": "12 avenue Mohammed V", "city": "Rabat", "postalCode": "10000", "country": "MA"}
                }
                """.formatted(email, nationalId);
    }
}
