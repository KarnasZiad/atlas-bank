package ma.atlasbank.customer.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.customer.application.CustomerCommandService;
import ma.atlasbank.customer.application.CustomerQueryService;
import ma.atlasbank.customer.application.CustomerSearchCriteria;
import ma.atlasbank.customer.application.command.RegisterCustomerCommand;
import ma.atlasbank.customer.application.command.StatusChangeCommand;
import ma.atlasbank.customer.application.command.UpdateContactCommand;
import ma.atlasbank.customer.application.view.CustomerRevisionView;
import ma.atlasbank.customer.application.view.CustomerSummaryView;
import ma.atlasbank.customer.application.view.CustomerView;
import ma.atlasbank.platform.security.AdminOnly;
import ma.atlasbank.platform.security.CustomerOnly;
import ma.atlasbank.platform.security.CustomerOrStaff;
import ma.atlasbank.platform.security.StaffOnly;
import ma.atlasbank.platform.web.Locations;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/customers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Customers", description = "Customer onboarding, KYC lifecycle and profile")
class CustomerController {

    private final CustomerCommandService commands;
    private final CustomerQueryService queries;

    @StaffOnly
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Register a customer", description = "The file starts pending verification by another employee.")
    ResponseEntity<CustomerView> register(@Valid @RequestBody RegisterCustomerCommand command, Requester requester) {
        CustomerView customer = commands.register(command, requester);
        return ResponseEntity.created(Locations.childOfCurrentRequest(customer.id())).body(customer);
    }

    @StaffOnly
    @GetMapping
    @Operation(summary = "Search the customer directory")
    PagedModel<CustomerSummaryView> search(
            @Valid @ParameterObject CustomerSearchCriteria criteria,
            @ParameterObject @PageableDefault(sort = "lastName") Pageable pageable) {
        return new PagedModel<>(queries.search(criteria, pageable));
    }

    @CustomerOnly
    @GetMapping("/me")
    @Operation(summary = "Profile of the signed-in customer")
    CustomerView me(Requester requester) {
        return queries.current(requester);
    }

    @CustomerOrStaff
    @GetMapping("/{customerId}")
    @Operation(summary = "Get a customer profile", description = "Customers can only read their own profile.")
    CustomerView get(@PathVariable UUID customerId, Requester requester) {
        return queries.get(customerId, requester);
    }

    @CustomerOrStaff
    @PutMapping(path = "/{customerId}/contact", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update phone number and postal address")
    CustomerView updateContact(
            @PathVariable UUID customerId, @Valid @RequestBody UpdateContactCommand command, Requester requester) {
        return commands.updateContact(customerId, command, requester);
    }

    @StaffOnly
    @PostMapping("/{customerId}/verification")
    @Operation(summary = "Approve the KYC file", description = "Four-eyes principle: the approver must differ from the registrar.")
    CustomerView verify(@PathVariable UUID customerId, Requester requester) {
        return commands.verify(customerId, requester);
    }

    @StaffOnly
    @PostMapping(path = "/{customerId}/suspension", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Suspend a customer", description = "Disables sign-in and freezes the customer's accounts.")
    CustomerView suspend(@PathVariable UUID customerId, @Valid @RequestBody StatusChangeCommand command) {
        return commands.suspend(customerId, command.reason());
    }

    @StaffOnly
    @PostMapping("/{customerId}/reinstatement")
    @Operation(summary = "Lift a suspension")
    CustomerView reinstate(@PathVariable UUID customerId) {
        return commands.reinstate(customerId);
    }

    @AdminOnly
    @PostMapping(path = "/{customerId}/closure", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Close the relationship (irreversible)")
    CustomerView close(@PathVariable UUID customerId, @Valid @RequestBody StatusChangeCommand command) {
        return commands.close(customerId, command.reason());
    }

    @AdminOnly
    @GetMapping("/{customerId}/history")
    @Operation(summary = "Audit trail of a customer", description = "Every committed change with its author, newest first.")
    PagedModel<CustomerRevisionView> history(@PathVariable UUID customerId, @ParameterObject Pageable pageable) {
        return new PagedModel<>(queries.history(customerId, pageable));
    }
}
