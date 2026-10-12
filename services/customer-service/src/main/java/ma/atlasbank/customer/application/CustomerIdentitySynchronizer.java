package ma.atlasbank.customer.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.atlasbank.contracts.customer.CustomerSnapshot;
import ma.atlasbank.customer.application.port.IdentityProvider;
import ma.atlasbank.customer.application.port.IdentityProvider.CustomerAccount;
import ma.atlasbank.customer.domain.Customer;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reconciles the sign-in account with the customer status: an account exists and is enabled
 * exactly when the customer is active.
 *
 * <p>Runs asynchronously after commit. The outbox records each delivery, so a failure (identity
 * provider unavailable...) is retried later instead of being lost; the reconciliation is
 * idempotent, so retries are safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CustomerIdentitySynchronizer {

    private final CustomerFinder finder;
    private final IdentityProvider identityProvider;

    @ApplicationModuleListener
    void on(CustomerSnapshot snapshot) {
        Customer customer = finder.getById(snapshot.customerId());
        if (customer.getStatus().allowsSignIn()) {
            grantAccess(customer);
        } else if (customer.isIdentityProvisioned()) {
            identityProvider.disableAccount(customer.getIdentityUserId());
            log.info("Sign-in disabled for customer {} ({})", customer.getId(), customer.getStatus());
        }
    }

    private void grantAccess(Customer customer) {
        if (customer.isIdentityProvisioned()) {
            identityProvider.enableAccount(customer.getIdentityUserId());
            return;
        }
        String accountId = identityProvider.provisionCustomerAccount(new CustomerAccount(
                customer.getId(),
                customer.getEmail(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getPreferredLanguage()));
        customer.linkIdentity(accountId);
        log.info("Sign-in account provisioned for customer {}", customer.getId());
    }
}
