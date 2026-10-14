package ma.atlasbank.customer.application.port;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Sign-in accounts of customers at the identity provider. Implementations must be idempotent:
 * provisioning an account that already exists returns the existing one.
 */
public interface IdentityProvider {

    /**
     * Creates (or finds) the sign-in account bound to the customer and asks the customer, by
     * e-mail, to verify the address and choose a password.
     *
     * @return identifier of the account at the identity provider
     */
    String provisionCustomerAccount(CustomerAccount account);

    /**
     * @return the identifier of the sign-in account bound to this customer, if one exists
     */
    Optional<String> findCustomerAccount(CustomerAccount account);

    void enableAccount(String accountId);

    void disableAccount(String accountId);

    record CustomerAccount(UUID customerId, String email, String firstName, String lastName, Locale language) {
    }
}
