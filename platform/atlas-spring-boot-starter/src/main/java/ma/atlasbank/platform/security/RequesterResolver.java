package ma.atlasbank.platform.security;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.commons.security.Role;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Builds the framework-free {@link Requester} from a validated JWT authentication.
 */
public class RequesterResolver {

    private final String customerIdClaim;
    private final RoleHierarchy roleHierarchy;
    private final Map<String, Role> rolesByAuthority;

    public RequesterResolver(String customerIdClaim, String rolePrefix, RoleHierarchy roleHierarchy) {
        this.customerIdClaim = customerIdClaim;
        this.roleHierarchy = roleHierarchy;
        this.rolesByAuthority = Arrays.stream(Role.values())
                .collect(Collectors.toUnmodifiableMap(role -> rolePrefix + role.name(), Function.identity()));
    }

    public Requester resolve(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AuthenticationCredentialsNotFoundException("A bearer token authentication is required");
        }
        Jwt jwt = token.getToken();
        return new Requester(jwt.getSubject(), token.getName(), customerId(jwt), roles(token));
    }

    private Set<Role> roles(JwtAuthenticationToken token) {
        return roleHierarchy.getReachableGrantedAuthorities(token.getAuthorities()).stream()
                .map(GrantedAuthority::getAuthority)
                .map(rolesByAuthority::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private UUID customerId(Jwt jwt) {
        String value = jwt.getClaimAsString(customerIdClaim);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            throw new InvalidBearerTokenException("Claim '%s' is not a valid identifier".formatted(customerIdClaim));
        }
    }
}
