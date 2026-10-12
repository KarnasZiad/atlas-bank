package ma.atlasbank.account.application;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.atlasbank.account.application.port.AccountHolderRepository;
import ma.atlasbank.account.application.port.AccountRepository;
import ma.atlasbank.account.domain.Account;
import ma.atlasbank.account.domain.AccountHolder;
import ma.atlasbank.account.domain.AccountStatus;
import ma.atlasbank.account.domain.FreezeCause;
import ma.atlasbank.account.domain.HolderStatus;
import ma.atlasbank.contracts.customer.CustomerSnapshot;
import ma.atlasbank.platform.time.BusinessCalendar;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the local view of account holders in sync with the customer service and applies the
 * consequences: suspending or closing a customer freezes their accounts, reinstating the
 * customer releases exactly the accounts that the suspension had frozen.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AccountHolderProjector {

    private static final String SUSPENSION_REASON = "Customer relationship suspended or closed";

    private final AccountHolderRepository holders;
    private final AccountRepository accounts;
    private final AccountEventFactory events;
    private final ApplicationEventPublisher publisher;
    private final BusinessCalendar calendar;

    public void apply(CustomerSnapshot snapshot) {
        HolderStatus status = HolderStatus.valueOf(snapshot.status().name());
        AccountHolder holder = holders.findById(snapshot.customerId()).orElse(null);
        if (holder == null) {
            holder = holders.save(AccountHolder.of(snapshot.customerId(), status, snapshot.version(), calendar.now()));
        } else if (!holder.apply(status, snapshot.version(), calendar.now())) {
            log.debug("Stale snapshot v{} of customer {} ignored", snapshot.version(), snapshot.customerId());
            return;
        }
        if (holder.isBlocked()) {
            freezeAccountsOf(holder);
        } else if (holder.canOpenAccounts()) {
            releaseAccountsOf(holder);
        }
    }

    private void freezeAccountsOf(AccountHolder holder) {
        List<Account> active = accounts.findAllByCustomerIdAndStatus(holder.getCustomerId(), AccountStatus.ACTIVE);
        active.forEach(account -> {
            account.freeze(FreezeCause.CUSTOMER_SUSPENDED, SUSPENSION_REASON);
            publisher.publishEvent(events.statusChanged(account, AccountStatus.ACTIVE));
        });
        log.info("{} account(s) frozen after customer {} became {}", active.size(), holder.getCustomerId(), holder.getStatus());
    }

    private void releaseAccountsOf(AccountHolder holder) {
        accounts.findAllByCustomerIdAndStatus(holder.getCustomerId(), AccountStatus.FROZEN).stream()
                .filter(Account::releaseCustomerSuspension)
                .forEach(account -> publisher.publishEvent(events.statusChanged(account, AccountStatus.FROZEN)));
    }
}
