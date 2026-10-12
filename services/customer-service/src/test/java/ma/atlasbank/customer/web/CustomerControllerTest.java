package ma.atlasbank.customer.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import ma.atlasbank.commons.error.ConflictException;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.customer.application.CustomerCommandService;
import ma.atlasbank.customer.application.CustomerQueryService;
import ma.atlasbank.customer.application.command.RegisterCustomerCommand;
import ma.atlasbank.customer.application.view.AddressView;
import ma.atlasbank.customer.application.view.CustomerView;
import ma.atlasbank.customer.domain.CustomerErrorCode;
import ma.atlasbank.customer.domain.CustomerStatus;
import ma.atlasbank.testsupport.security.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    private static final String CUSTOMERS = "/api/v1/customers";
    private static final String VALID_REGISTRATION = """
            {
              "firstName": "Salma",
              "lastName": "Benali",
              "email": "salma.benali@example.ma",
              "phoneNumber": "0612345678",
              "dateOfBirth": "1990-05-17",
              "nationalId": "AB123456",
              "address": {"line": "12 avenue Mohammed V", "city": "Rabat", "postalCode": "10000", "country": "MA"}
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerCommandService commands;

    @MockitoBean
    private CustomerQueryService queries;

    @Test
    void rejectsAnonymousCalls() throws Exception {
        mvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTRATION))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(commands);
    }

    @Test
    void forbidsCustomersToRegisterCustomers() throws Exception {
        mvc.perform(post(CUSTOMERS).with(TestTokens.customer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTRATION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(commands);
    }

    @Test
    void letsEmployeesRegisterCustomers() throws Exception {
        CustomerView registered = view(UUID.randomUUID());
        given(commands.register(any(RegisterCustomerCommand.class), any(Requester.class))).willReturn(registered);

        mvc.perform(post(CUSTOMERS).with(TestTokens.employee("agent.amina"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTRATION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/customers/" + registered.id()))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.nationalId").value("******56"));
    }

    @Test
    void reportsInvalidFieldsAsProblemDetails() throws Exception {
        String invalid = VALID_REGISTRATION.replace("salma.benali@example.ma", "not-an-email").replace("AB123456", "12");

        mvc.perform(post(CUSTOMERS).with(TestTokens.employee())
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.instance").value(CUSTOMERS));
    }

    @Test
    void mapsBusinessConflictsToProblemDetails() throws Exception {
        given(commands.register(any(), any())).willThrow(new ConflictException(
                CustomerErrorCode.EMAIL_ALREADY_REGISTERED, "A customer is already registered with this e-mail address"));

        mvc.perform(post(CUSTOMERS).with(TestTokens.employee())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTRATION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.type").value("urn:atlasbank:problem:email-already-registered"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void resolvesTheSignedInCustomerFromTheToken() throws Exception {
        UUID customerId = UUID.randomUUID();
        given(queries.current(any(Requester.class))).willReturn(view(customerId));

        mvc.perform(get(CUSTOMERS + "/me").with(TestTokens.customer(customerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId.toString()));
    }

    @Test
    void restrictsTheAuditTrailToAdministrators() throws Exception {
        mvc.perform(get(CUSTOMERS + "/{id}/history", UUID.randomUUID()).with(TestTokens.employee()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(queries);
    }

    @Test
    void administratorsInheritEmployeePermissions() throws Exception {
        UUID customerId = UUID.randomUUID();
        given(commands.verify(eq(customerId), any(Requester.class))).willReturn(view(customerId));

        mvc.perform(post(CUSTOMERS + "/{id}/verification", customerId).with(TestTokens.admin()))
                .andExpect(status().isOk());
    }

    private static CustomerView view(UUID id) {
        return new CustomerView(id, "Salma", "Benali", "salma.benali@example.ma", "+212612345678",
                LocalDate.of(1990, 5, 17), "******56",
                new AddressView("12 avenue Mohammed V", "Rabat", "10000", "MA"), "fr",
                CustomerStatus.PENDING_VERIFICATION, null, false, "agent.amina", null, null,
                Instant.parse("2026-10-12T09:00:00Z"), Instant.parse("2026-10-12T09:00:00Z"));
    }
}
