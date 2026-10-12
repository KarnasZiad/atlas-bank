package ma.atlasbank.customer.domain;

import java.util.Set;

/**
 * Customer lifecycle. Transitions are declared here, once, and enforced by the aggregate.
 *
 * <pre>
 * PENDING_VERIFICATION ──verify──► ACTIVE ◄──reinstate── SUSPENDED
 *          │                         │ └──────suspend──────►  │
 *          └──────────close──────────┴────────close───────────┴──► CLOSED
 * </pre>
 */
public enum CustomerStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    CLOSED;

    private Set<CustomerStatus> allowedTargets() {
        return switch (this) {
            case PENDING_VERIFICATION -> Set.of(ACTIVE, CLOSED);
            case ACTIVE -> Set.of(SUSPENDED, CLOSED);
            case SUSPENDED -> Set.of(ACTIVE, CLOSED);
            case CLOSED -> Set.of();
        };
    }

    public boolean canTransitionTo(CustomerStatus target) {
        return allowedTargets().contains(target);
    }

    /**
     * Only active customers may sign in to digital channels.
     */
    public boolean allowsSignIn() {
        return this == ACTIVE;
    }
}
