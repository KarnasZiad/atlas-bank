package ma.atlasbank.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.account.application.AccountCommandService;
import ma.atlasbank.account.application.AccountQueryService;
import ma.atlasbank.account.application.AccountSearchCriteria;
import ma.atlasbank.account.application.LedgerReconciliation;
import ma.atlasbank.account.application.command.OpenAccountCommand;
import ma.atlasbank.account.application.view.AccountView;
import ma.atlasbank.account.domain.AccountErrorCode;
import ma.atlasbank.account.domain.AccountStatus;
import ma.atlasbank.account.domain.AccountType;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.testsupport.security.TestTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    private static final String ACCOUNTS = "/api/v1/accounts";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountCommandService commands;

    @MockitoBean
    private AccountQueryService queries;

    @MockitoBean
    private LedgerReconciliation reconciliation;

    @Test
    void onlyAdministratorsReconcileTheBooks() throws Exception {
        mvc.perform(get(ACCOUNTS + "/reconciliation").with(TestTokens.employee())).andExpect(status().isForbidden());
        mvc.perform(get(ACCOUNTS + "/reconciliation").with(TestTokens.admin())).andExpect(status().isOk());
    }

    @Test
    void onlyStaffOpenAccounts() throws Exception {
        String body = """
                {"customerId": "%s", "type": "CURRENT", "currency": "MAD"}
                """.formatted(UUID.randomUUID());

        mvc.perform(post(ACCOUNTS).with(TestTokens.customer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verifyNoInteractions(commands);

        AccountView opened = view(UUID.randomUUID());
        given(commands.open(any(OpenAccountCommand.class))).willReturn(opened);
        mvc.perform(post(ACCOUNTS).with(TestTokens.employee()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", ACCOUNTS + "/" + opened.id()));
    }

    @Test
    void passesTheCallerToTheQueryServiceSoThatCustomersAreScoped() throws Exception {
        UUID customerId = UUID.randomUUID();
        given(queries.search(any(), any(), any())).willReturn(Page.empty());

        mvc.perform(get(ACCOUNTS).param("customerId", UUID.randomUUID().toString())
                        .with(TestTokens.customer(customerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        ArgumentCaptor<Requester> caller = ArgumentCaptor.forClass(Requester.class);
        verify(queries).search(any(AccountSearchCriteria.class), caller.capture(), any(Pageable.class));
        assertThat(caller.getValue().customerId()).isEqualTo(customerId);
        assertThat(caller.getValue().isStaff()).isFalse();
    }

    @Test
    void freezingIsAStaffDecision() throws Exception {
        UUID accountId = UUID.randomUUID();
        String reason = """
                {"reason": "Suspicious activity"}
                """;

        mvc.perform(post(ACCOUNTS + "/{id}/freeze", accountId).with(TestTokens.customer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(reason))
                .andExpect(status().isForbidden());

        given(commands.freeze(eq(accountId), eq("Suspicious activity"), any(Requester.class))).willReturn(view(accountId));
        mvc.perform(post(ACCOUNTS + "/{id}/freeze", accountId).with(TestTokens.employee())
                        .contentType(MediaType.APPLICATION_JSON).content(reason))
                .andExpect(status().isOk());
    }

    @Test
    void onlyASupervisorLiftsAFreeze() throws Exception {
        UUID accountId = UUID.randomUUID();
        given(commands.unfreeze(eq(accountId), any(Requester.class))).willReturn(view(accountId));

        mvc.perform(post(ACCOUNTS + "/{id}/unfreeze", accountId).with(TestTokens.employee()))
                .andExpect(status().isForbidden());
        mvc.perform(post(ACCOUNTS + "/{id}/unfreeze", accountId).with(TestTokens.admin()))
                .andExpect(status().isOk());
    }

    @Test
    void reportsBusinessRulesAsUnprocessable() throws Exception {
        UUID accountId = UUID.randomUUID();
        given(commands.close(eq(accountId), any())).willThrow(new BusinessRuleViolationException(
                AccountErrorCode.BALANCE_NOT_ZERO, "Account must have a zero balance to be closed"));

        mvc.perform(post(ACCOUNTS + "/{id}/closure", accountId).with(TestTokens.employee())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Customer request\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BALANCE_NOT_ZERO"));
    }

    @Test
    void validatesStatementDates() throws Exception {
        mvc.perform(get(ACCOUNTS + "/{id}/entries", UUID.randomUUID()).param("from", "not-a-date")
                        .with(TestTokens.employee()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private static AccountView view(UUID id) {
        return new AccountView(id, "MA64999001000000000000000001", UUID.randomUUID(), AccountType.CURRENT, "MAD",
                new BigDecimal("0.00"), new BigDecimal("5000.00"), new BigDecimal("5000.00"),
                new BigDecimal("50000.00"), AccountStatus.ACTIVE, null, Instant.now(), null);
    }
}
