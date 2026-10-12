package ma.atlasbank.platform.security;

import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Name of whoever triggered the current unit of work, for audit columns and audit revisions.
 * Work started by a message listener or a scheduler is attributed to the system.
 */
public final class CurrentActor {

    public static final String SYSTEM = "system";

    private CurrentActor() {
    }

    public static String name() {
        return authenticatedName().orElse(SYSTEM);
    }

    public static Optional<String> authenticatedName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.ofNullable(authentication.getName());
    }
}
