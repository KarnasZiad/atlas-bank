package ma.atlasbank.account.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.account.application.AccountCommandService;
import ma.atlasbank.account.application.AccountQueryService;
import ma.atlasbank.account.application.AccountSearchCriteria;
import ma.atlasbank.account.application.LedgerReconciliation;
import ma.atlasbank.account.application.command.OpenAccountCommand;
import ma.atlasbank.account.application.command.StatusChangeCommand;
import ma.atlasbank.account.application.view.AccountView;
import ma.atlasbank.account.application.view.LedgerEntryView;
import ma.atlasbank.account.application.view.ReconciliationReport;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.platform.security.AdminOnly;
import ma.atlasbank.platform.security.CustomerOrStaff;
import ma.atlasbank.platform.security.StaffOnly;
import ma.atlasbank.platform.web.Locations;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Accounts", description = "Bank accounts, balances and statements")
class AccountController {

    private final AccountCommandService commands;
    private final AccountQueryService queries;
    private final LedgerReconciliation reconciliation;

    @StaffOnly
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Open an account", description = "The holder must be an active customer.")
    ResponseEntity<AccountView> open(@Valid @RequestBody OpenAccountCommand command) {
        AccountView account = commands.open(command);
        return ResponseEntity.created(Locations.childOfCurrentRequest(account.id())).body(account);
    }

    @CustomerOrStaff
    @GetMapping
    @Operation(summary = "List accounts", description = "Customers always get their own accounts only.")
    PagedModel<AccountView> list(
            @ParameterObject AccountSearchCriteria criteria,
            @ParameterObject @PageableDefault(sort = "openedAt", direction = Sort.Direction.DESC) Pageable pageable,
            Requester requester) {
        return new PagedModel<>(queries.search(criteria, requester, pageable));
    }

    @AdminOnly
    @GetMapping("/reconciliation")
    @Operation(summary = "Check the books", description = "Balances against their entries, and every transaction balanced.")
    ReconciliationReport reconciliation() {
        return reconciliation.reconcile();
    }

    @CustomerOrStaff
    @GetMapping("/{accountId}")
    @Operation(summary = "Get an account with its balances")
    AccountView get(@PathVariable UUID accountId, Requester requester) {
        return queries.get(accountId, requester);
    }

    @CustomerOrStaff
    @GetMapping("/{accountId}/entries")
    @Operation(summary = "Account statement", description = "Ledger entries between two business days (inclusive).")
    PagedModel<LedgerEntryView> statement(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(sort = "bookedAt", direction = Sort.Direction.DESC) Pageable pageable,
            Requester requester) {
        return new PagedModel<>(queries.statement(accountId, from, to, requester, pageable));
    }

    @StaffOnly
    @PostMapping(path = "/{accountId}/freeze", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Freeze debits on an account")
    AccountView freeze(@PathVariable UUID accountId, @Valid @RequestBody StatusChangeCommand command,
                       Requester requester) {
        return commands.freeze(accountId, command.reason(), requester);
    }

    @AdminOnly
    @PostMapping("/{accountId}/unfreeze")
    @Operation(summary = "Lift a manual freeze",
            description = "Supervisor decision, by another person than the one who froze the account.")
    AccountView unfreeze(@PathVariable UUID accountId, Requester requester) {
        return commands.unfreeze(accountId, requester);
    }

    @StaffOnly
    @PostMapping(path = "/{accountId}/closure", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Close an account", description = "The balance must be zero.")
    AccountView close(@PathVariable UUID accountId, @Valid @RequestBody StatusChangeCommand command) {
        return commands.close(accountId, command.reason());
    }
}
