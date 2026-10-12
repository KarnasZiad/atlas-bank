package ma.atlasbank.commons.security;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ma.atlasbank.commons.error.OperationNotPermittedException;

/**
 * Framework-independent view of the authenticated caller, handed to application services
 * so that ownership rules can be enforced without depending on Spring Security.
 *
 * @param subject    immutable identifier of the user at the identity provider
 * @param username   human-readable login, used in audit trails
 * @param customerId customer the user is bound to; {@code null} for bank staff
 * @param roles      effective roles, hierarchy already applied
 */
public record Requester(String subject, String username, UUID customerId, Set<Role> roles) {

    public Requester {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(username, "username must not be null");
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isStaff() {
        return hasRole(Role.EMPLOYEE) || hasRole(Role.ADMIN);
    }

    public Optional<UUID> customer() {
        return Optional.ofNullable(customerId);
    }

    /**
     * Customer the user is bound to, for operations that only make sense for a customer.
     */
    public UUID requireCustomerId() {
        if (customerId == null) {
            throw new OperationNotPermittedException(AccessErrorCode.CUSTOMER_PROFILE_NOT_LINKED,
                    "No customer profile is linked to user %s", username);
        }
        return customerId;
    }

    /**
     * Staff may act on any customer; a customer only on their own data.
     */
    public boolean canAccessCustomer(UUID ownerCustomerId) {
        return isStaff() || (customerId != null && customerId.equals(ownerCustomerId));
    }
}
