package ma.atlasbank.transaction.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.money.Money;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.transaction.application.command.CashOperationCommand;
import ma.atlasbank.transaction.application.command.TransferCommand;
import ma.atlasbank.transaction.application.port.PaymentOrderRepository;
import ma.atlasbank.transaction.application.port.ReferenceGenerator;
import ma.atlasbank.transaction.application.view.OrderSubmission;
import ma.atlasbank.transaction.domain.Initiator;
import ma.atlasbank.transaction.domain.InitiatorType;
import ma.atlasbank.transaction.domain.OrderRequest;
import ma.atlasbank.transaction.domain.PaymentOrder;
import ma.atlasbank.transaction.domain.TransactionErrorCode;
import ma.atlasbank.platform.time.BusinessCalendar;
import org.iban4j.IbanUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts payment orders. Intake is idempotent per initiator and idempotency key, and an accepted
 * order reaches the ledger through the transactional outbox: it is either stored <em>and</em>
 * published, or neither.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentOrderIntakeService {

    private final PaymentOrderRepository orders;
    private final ReferenceGenerator references;
    private final TransactionSettings settings;
    private final PaymentOrderMapper mapper;
    private final TransactionRequestedFactory events;
    private final ApplicationEventPublisher publisher;
    private final BusinessCalendar calendar;

    public OrderSubmission submitTransfer(TransferCommand command, String idempotencyKey, Requester requester) {
        requireValidAccountNumber(command.sourceAccountNumber());
        requireValidAccountNumber(command.targetAccountNumber());
        return submit(OrderRequest.transfer(command.sourceAccountNumber(), command.targetAccountNumber(),
                amountOf(command.amount(), command.currency()), command.description()), idempotencyKey, requester);
    }

    public OrderSubmission submitDeposit(CashOperationCommand command, String idempotencyKey, Requester requester) {
        requireValidAccountNumber(command.accountNumber());
        return submit(OrderRequest.deposit(command.accountNumber(),
                amountOf(command.amount(), command.currency()), command.description()), idempotencyKey, requester);
    }

    public OrderSubmission submitWithdrawal(CashOperationCommand command, String idempotencyKey, Requester requester) {
        requireValidAccountNumber(command.accountNumber());
        return submit(OrderRequest.withdrawal(command.accountNumber(),
                amountOf(command.amount(), command.currency()), command.description()), idempotencyKey, requester);
    }

    private OrderSubmission submit(OrderRequest request, String idempotencyKey, Requester requester) {
        String fingerprint = fingerprint(request);
        Optional<PaymentOrder> previous = orders.findByInitiatorSubjectAndIdempotencyKey(requester.subject(), idempotencyKey);
        if (previous.isPresent()) {
            return replay(previous.get(), fingerprint);
        }
        PaymentOrder order = orders.saveAndFlush(PaymentOrder.place(
                request, references.nextReference(request.kind()), initiatorOf(requester),
                idempotencyKey, fingerprint, calendar.now()));
        publisher.publishEvent(events.requested(order));
        return new OrderSubmission(mapper.toView(order), false);
    }

    /**
     * Same key, same content: the client is retrying, return the original order.
     * Same key, different content: a client bug that must not be silently "fixed".
     */
    private OrderSubmission replay(PaymentOrder previous, String fingerprint) {
        if (!previous.isSameRequestAs(fingerprint)) {
            throw new BusinessRuleViolationException(TransactionErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This idempotency key was already used for a different request");
        }
        return new OrderSubmission(mapper.toView(previous), true);
    }

    private Money amountOf(BigDecimal amount, String currencyCode) {
        Money money = Money.of(amount, currencyCode);
        BigDecimal ceiling = settings.ceilingFor(money.currency())
                .orElseThrow(() -> new BusinessRuleViolationException(TransactionErrorCode.UNSUPPORTED_CURRENCY,
                        "Orders in %s are not accepted", money.currency()));
        if (money.amount().compareTo(ceiling) > 0) {
            throw new BusinessRuleViolationException(TransactionErrorCode.AMOUNT_ABOVE_LIMIT,
                    "A single order cannot exceed %s %s", ceiling.toPlainString(), money.currency());
        }
        return money;
    }

    private static Initiator initiatorOf(Requester requester) {
        return requester.isStaff()
                ? new Initiator(requester.subject(), requester.username(), null, InitiatorType.EMPLOYEE)
                : new Initiator(requester.subject(), requester.username(), requester.requireCustomerId(), InitiatorType.CUSTOMER);
    }

    /** IBAN structure and check digits are verified by iban4j (ISO 13616). */
    private static void requireValidAccountNumber(String accountNumber) {
        if (!IbanUtil.isValid(accountNumber)) {
            throw new BusinessRuleViolationException(TransactionErrorCode.INVALID_ACCOUNT_NUMBER,
                    "'%s' is not a valid IBAN", accountNumber);
        }
    }

    private static String fingerprint(OrderRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(request.canonicalForm().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is not available", unavailable);
        }
    }
}
