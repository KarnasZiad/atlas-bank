package ma.atlasbank.account.application;

import java.util.Currency;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.account.application.command.OpenAccountCommand;
import ma.atlasbank.account.application.port.AccountHolderRepository;
import ma.atlasbank.account.application.port.AccountNumberGenerator;
import ma.atlasbank.account.application.port.AccountRepository;
import ma.atlasbank.account.application.view.AccountView;
import ma.atlasbank.account.domain.Account;
import ma.atlasbank.account.domain.AccountErrorCode;
import ma.atlasbank.account.domain.AccountHolder;
import ma.atlasbank.account.domain.AccountLimits;
import ma.atlasbank.account.domain.AccountStatus;
import ma.atlasbank.account.domain.FreezeCause;
import ma.atlasbank.account.domain.StaffMember;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.money.Money;
import ma.atlasbank.platform.time.BusinessCalendar;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AccountCommandService {

    private final AccountRepository accounts;
    private final AccountHolderRepository holders;
    private final AccountFinder finder;
    private final AccountNumberGenerator accountNumbers;
    private final AccountSettings settings;
    private final AccountViewMapper mapper;
    private final AccountEventFactory events;
    private final ApplicationEventPublisher publisher;
    private final BusinessCalendar calendar;

    /**
     * Opens an account for an active customer. Eligibility is read from the local projection of
     * customers: no synchronous call to the customer service is needed.
     */
    public AccountView open(OpenAccountCommand command) {
        if (command.type().isInternal()) {
            throw new BusinessRuleViolationException(AccountErrorCode.INTERNAL_ACCOUNT,
                    "%s accounts belong to the bank and cannot be opened for a customer", command.type());
        }
        AccountHolder holder = holders.findById(command.customerId())
                .filter(AccountHolder::canOpenAccounts)
                .orElseThrow(() -> new BusinessRuleViolationException(AccountErrorCode.CUSTOMER_NOT_ELIGIBLE,
                        "Customer %s is unknown or not active", command.customerId()));
        Currency currency = Money.currencyOf(command.currency());
        AccountLimits limits = settings.limitsFor(command.type(), currency)
                .orElseThrow(() -> new BusinessRuleViolationException(AccountErrorCode.UNSUPPORTED_CURRENCY,
                        "%s accounts are not offered in %s", command.type(), currency));

        Account account = accounts.save(Account.open(
                accountNumbers.nextAccountNumber(), holder.getCustomerId(), command.type(), currency, limits, calendar.now()));
        publisher.publishEvent(events.opened(account));
        return mapper.toView(account);
    }

    public AccountView freeze(UUID accountId, String reason, Requester requester) {
        return changeStatus(accountId, account -> account.freeze(FreezeCause.MANUAL, reason, staffMember(requester)));
    }

    /**
     * Lifting a freeze is refused while the customer is suspended: the accounts of a suspended
     * customer stay frozen until the customer is reinstated. It is also refused to the employee who
     * decided the freeze (four eyes).
     */
    public AccountView unfreeze(UUID accountId, Requester requester) {
        return changeStatus(accountId, account -> {
            if (holders.findById(account.getCustomerId()).map(AccountHolder::isBlocked).orElse(false)) {
                throw new BusinessRuleViolationException(AccountErrorCode.CUSTOMER_NOT_ELIGIBLE,
                        "The holder of account %s is suspended", account.getAccountNumber());
            }
            account.unfreeze(staffMember(requester));
        });
    }

    private static StaffMember staffMember(Requester requester) {
        return new StaffMember(requester.subject(), requester.username());
    }

    public AccountView close(UUID accountId, String reason) {
        return changeStatus(accountId, account -> account.close(reason, calendar.now()));
    }

    private AccountView changeStatus(UUID accountId, Consumer<Account> change) {
        Account account = finder.getById(accountId);
        AccountStatus previous = account.getStatus();
        change.accept(account);
        if (account.getStatus() != previous) {
            publisher.publishEvent(events.statusChanged(account, previous));
        }
        return mapper.toView(accounts.save(account));
    }
}
