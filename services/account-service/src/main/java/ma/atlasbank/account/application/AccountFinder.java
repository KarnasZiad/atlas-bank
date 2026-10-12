package ma.atlasbank.account.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.account.application.port.AccountRepository;
import ma.atlasbank.account.domain.Account;
import ma.atlasbank.account.domain.AccountErrorCode;
import ma.atlasbank.commons.error.ResourceNotFoundException;
import ma.atlasbank.commons.security.Requester;
import org.springframework.stereotype.Component;

/**
 * Loads accounts and applies the ownership rule; other customers' accounts look non-existent.
 */
@Component
@RequiredArgsConstructor
class AccountFinder {

    private final AccountRepository accounts;

    Account getById(UUID accountId) {
        return accounts.findById(accountId).orElseThrow(() -> notFound(accountId));
    }

    Account getVisibleTo(UUID accountId, Requester requester) {
        Account account = getById(accountId);
        if (!requester.canAccessCustomer(account.getCustomerId())) {
            throw notFound(accountId);
        }
        return account;
    }

    private static ResourceNotFoundException notFound(UUID accountId) {
        return new ResourceNotFoundException(AccountErrorCode.ACCOUNT_NOT_FOUND, "Account %s not found", accountId);
    }
}
