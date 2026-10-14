package ma.atlasbank.transaction.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.platform.security.CustomerOrStaff;
import ma.atlasbank.platform.security.StaffOnly;
import ma.atlasbank.platform.web.Locations;
import ma.atlasbank.transaction.application.OrderSearchCriteria;
import ma.atlasbank.transaction.application.PaymentOrderIntakeService;
import ma.atlasbank.transaction.application.PaymentOrderQueryService;
import ma.atlasbank.transaction.application.PaymentOrderReviewService;
import ma.atlasbank.transaction.application.command.CashOperationCommand;
import ma.atlasbank.transaction.application.command.TransferCommand;
import ma.atlasbank.transaction.application.view.OrderSubmission;
import ma.atlasbank.transaction.application.view.PaymentOrderView;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment orders are asynchronous: intake answers {@code 202 Accepted} with the order to poll,
 * the ledger settles it a few milliseconds later. Every order requires an {@code Idempotency-Key}
 * header, so that a client can safely retry after a timeout without paying twice. Staff orders above
 * the four-eyes limits are accepted as {@code AWAITING_APPROVAL} until another employee reviews them.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Transactions", description = "Transfers, cash deposits and withdrawals")
class TransactionController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";
    private static final String KEY_FORMAT = "^[A-Za-z0-9_-]{16,64}$";
    private static final String ORDER_PATH = "/api/v1/transactions/{orderId}";

    private final PaymentOrderIntakeService intake;
    private final PaymentOrderReviewService reviews;
    private final PaymentOrderQueryService queries;

    @CustomerOrStaff
    @PostMapping(path = "/transfers", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Order a transfer", description = "Customers can only debit their own accounts.")
    ResponseEntity<PaymentOrderView> transfer(
            @Parameter(description = "Unique key of this request (16-64 characters), reused on retries")
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = KEY_FORMAT) String idempotencyKey,
            @Valid @RequestBody TransferCommand command,
            Requester requester) {
        return accepted(intake.submitTransfer(command, idempotencyKey, requester));
    }

    @StaffOnly
    @PostMapping(path = "/deposits", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Record a cash deposit at the counter")
    ResponseEntity<PaymentOrderView> deposit(
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = KEY_FORMAT) String idempotencyKey,
            @Valid @RequestBody CashOperationCommand command,
            Requester requester) {
        return accepted(intake.submitDeposit(command, idempotencyKey, requester));
    }

    @StaffOnly
    @PostMapping(path = "/withdrawals", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Record a cash withdrawal at the counter")
    ResponseEntity<PaymentOrderView> withdrawal(
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = KEY_FORMAT) String idempotencyKey,
            @Valid @RequestBody CashOperationCommand command,
            Requester requester) {
        return accepted(intake.submitWithdrawal(command, idempotencyKey, requester));
    }

    @StaffOnly
    @PostMapping("/{orderId}/approval")
    @Operation(summary = "Approve a staff order",
            description = "Four-eyes principle: the approver must differ from the employee who placed the order.")
    PaymentOrderView approve(@PathVariable UUID orderId, Requester requester) {
        return reviews.approve(orderId, requester);
    }

    @StaffOnly
    @PostMapping("/{orderId}/refusal")
    @Operation(summary = "Refuse a staff order awaiting approval", description = "Nothing reaches the ledger.")
    PaymentOrderView refuse(@PathVariable UUID orderId, Requester requester) {
        return reviews.refuse(orderId, requester);
    }

    @CustomerOrStaff
    @GetMapping("/{orderId}")
    @Operation(summary = "Track an order")
    PaymentOrderView get(@PathVariable UUID orderId, Requester requester) {
        return queries.get(orderId, requester);
    }

    @CustomerOrStaff
    @GetMapping
    @Operation(summary = "Order history", description = "Customers get the orders they placed.")
    PagedModel<PaymentOrderView> search(
            @Valid @ParameterObject OrderSearchCriteria criteria,
            @ParameterObject @PageableDefault(sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable,
            Requester requester) {
        return new PagedModel<>(queries.search(criteria, requester, pageable));
    }

    private static ResponseEntity<PaymentOrderView> accepted(OrderSubmission submission) {
        return ResponseEntity.accepted()
                .location(Locations.of(ORDER_PATH, submission.order().id()))
                .header(IDEMPOTENT_REPLAYED, String.valueOf(submission.replayed()))
                .body(submission.order());
    }
}
