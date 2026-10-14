package ma.atlasbank.account.application;

import java.math.BigDecimal;
import java.util.Currency;
import ma.atlasbank.account.application.view.AccountView;
import ma.atlasbank.account.application.view.LedgerEntryView;
import ma.atlasbank.account.domain.Account;
import ma.atlasbank.account.domain.LedgerEntry;
import ma.atlasbank.commons.money.Money;
import ma.atlasbank.commons.security.Requester;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AccountViewMapper {

    @Mapping(target = "balance", expression = "java(account.balance().amount())")
    @Mapping(target = "availableBalance", expression = "java(account.availableBalance().amount())")
    @Mapping(target = "overdraftLimit", expression = "java(account.overdraft().amount())")
    @Mapping(target = "dailyDebitLimit", expression = "java(account.dailyDebitAllowance().amount())")
    AccountView toView(Account account);

    /**
     * A freeze reason may refer to an investigation: the customer sees that the account is frozen,
     * never why.
     */
    default AccountView toViewFor(Account account, Requester requester) {
        AccountView view = toView(account);
        if (requester.isStaff()) {
            return view;
        }
        return new AccountView(view.id(), view.accountNumber(), view.customerId(), view.type(), view.currency(),
                view.balance(), view.availableBalance(), view.overdraftLimit(), view.dailyDebitLimit(), view.status(),
                null, view.openedAt(), view.closedAt());
    }

    @Mapping(target = "amount", expression = "java(entry.money().amount())")
    @Mapping(target = "balanceAfter", expression = "java(scaled(entry.getBalanceAfter(), entry.getCurrency()))")
    @Mapping(target = "kind", source = "kind")
    LedgerEntryView toView(LedgerEntry entry);

    default String currencyCode(Currency currency) {
        return currency.getCurrencyCode();
    }

    /** Balances can be negative (overdraft) but always carry the currency's number of decimals. */
    default BigDecimal scaled(BigDecimal amount, Currency currency) {
        return Money.of(amount, currency).amount();
    }
}
