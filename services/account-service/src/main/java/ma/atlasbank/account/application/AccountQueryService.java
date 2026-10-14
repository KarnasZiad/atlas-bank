package ma.atlasbank.account.application;

import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.account.application.port.AccountRepository;
import ma.atlasbank.account.application.port.LedgerEntryRepository;
import ma.atlasbank.account.application.view.AccountView;
import ma.atlasbank.account.application.view.LedgerEntryView;
import ma.atlasbank.account.domain.Account;
import ma.atlasbank.account.domain.AccountErrorCode;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.platform.time.BusinessCalendar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountQueryService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final AccountFinder finder;
    private final AccountViewMapper mapper;
    private final AccountSettings settings;
    private final BusinessCalendar calendar;

    public AccountView get(UUID accountId, Requester requester) {
        return mapper.toViewFor(finder.getVisibleTo(accountId, requester), requester);
    }

    /**
     * Staff can browse every account; customers only ever see their own, whatever they ask for.
     */
    public Page<AccountView> search(AccountSearchCriteria criteria, Requester requester, Pageable pageable) {
        AccountSearchCriteria effective = requester.isStaff() ? criteria : criteria.ownedBy(requester.requireCustomerId());
        return accounts.search(effective, pageable).map(account -> mapper.toViewFor(account, requester));
    }

    /**
     * Ledger entries booked between two business days, both included. Without dates, the
     * statement covers the configured number of most recent days.
     */
    public Page<LedgerEntryView> statement(UUID accountId, LocalDate from, LocalDate to, Requester requester,
                                           Pageable pageable) {
        Account account = finder.getVisibleTo(accountId, requester);
        LocalDate lastDay = to != null ? to : calendar.today();
        LocalDate firstDay = from != null ? from : lastDay.minusDays(settings.statementDaysByDefault() - 1L);
        if (firstDay.isAfter(lastDay)) {
            throw new BusinessRuleViolationException(AccountErrorCode.INVALID_PERIOD,
                    "The statement period starts after it ends");
        }
        return ledger.findStatement(account.getId(), calendar.startOf(firstDay), calendar.endOf(lastDay), pageable)
                .map(mapper::toView);
    }
}
