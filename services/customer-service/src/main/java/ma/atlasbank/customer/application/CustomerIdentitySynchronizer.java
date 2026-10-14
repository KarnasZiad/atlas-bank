package ma.atlasbank.customer.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.atlasbank.contracts.customer.CustomerSnapshot;
import ma.atlasbank.customer.application.port.IdentityProvider;
import ma.atlasbank.customer.application.port.IdentityProvider.CustomerAccount;
import ma.atlasbank.customer.domain.Customer;
import ma.atlasbank.customer.domain.CustomerStatus;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reconciles the sign-in account with the customer status: an account exists and is enabled
 * exactly when the customer is active.
 *
 * <p>Runs asynchronously after commit. The outbox records each delivery, so a failure (identity
 * provider unavailable...) is retried later instead of being lost; the reconciliation is
 * idempotent, so retries are safe.
 *
 * <p>The customer row stays locked during the reconciliation: a suspension cannot commit while an
 * account is being provisioned (it is told to retry), so the decision is never taken on a status
 * that changed meanwhile.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CustomerIdentitySynchronizer {

    private final CustomerFinder finder;
    private final IdentityProvider identityProvider;

    @ApplicationModuleListener
    void on(CustomerSnapshot snapshot) {
        Customer customer = finder.lockById(snapshot.customerId());
        if (customer.getStatus().allowsSignIn()) {
            grantAccess(customer);
        } else if (customer.isIdentityProvisioned()) {
            identityProvider.disableAccount(customer.getIdentityUserId());
            log.info("Sign-in disabled for customer {} ({})", customer.getId(), customer.getStatus());
        } else if (customer.getStatus() != CustomerStatus.PENDING_VERIFICATION) {
            revokeUnlinkedAccount(customer);
        }
    }

    private void grantAccess(Customer customer) {
        if (customer.isIdentityProvisioned()) {
            identityProvider.enableAccount(customer.getIdentityUserId());
            return;
        }
        String accountId = identityProvider.provisionCustomerAccount(accountOf(customer));
        customer.linkIdentity(accountId);
        log.info("Sign-in account provisioned for customer {}", customer.getId());
    }

    /**
     * An account may exist without being linked: created at the identity provider, then the link
     * failed to commit. Once the customer is no longer active, such an account must not stay usable.
     */
    private void revokeUnlinkedAccount(Customer customer) {
        identityProvider.findCustomerAccount(accountOf(customer)).ifPresent(accountId -> {
            identityProvider.disableAccount(accountId);
            customer.linkIdentity(accountId);
            log.warn("Unlinked sign-in account of customer {} found and disabled ({})", customer.getId(), customer.getStatus());
        });
    }

    private static CustomerAccount accountOf(Customer customer) {
        return new CustomerAccount(
                customer.getId(),
                customer.getEmail(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getPreferredLanguage());
    }
}
