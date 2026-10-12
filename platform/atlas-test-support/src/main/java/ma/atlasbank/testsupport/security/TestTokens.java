package ma.atlasbank.testsupport.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Arrays;
import java.util.UUID;
import ma.atlasbank.commons.security.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Builds authenticated MockMvc requests that look exactly like Keycloak-issued tokens
 * (roles as {@code ROLE_*} authorities, {@code preferred_username}, {@code customer_id}).
 */
public final class TestTokens {

    private TestTokens() {
    }

    public static JwtRequestPostProcessor customer(UUID customerId) {
        return token("customer-" + customerId, Role.CUSTOMER)
                .jwt(jwt -> jwt.claim("customer_id", customerId.toString()));
    }

    public static JwtRequestPostProcessor employee() {
        return token("agent.test", Role.EMPLOYEE);
    }

    public static JwtRequestPostProcessor employee(String username) {
        return token(username, Role.EMPLOYEE);
    }

    public static JwtRequestPostProcessor admin() {
        return token("admin.test", Role.ADMIN);
    }

    public static JwtRequestPostProcessor token(String username, Role... roles) {
        return jwt()
                // Subject and username are aligned so that the authentication name matches production,
                // where the principal is read from the "preferred_username" claim.
                .jwt(jwt -> jwt.subject(username).claim("preferred_username", username))
                .authorities(Arrays.stream(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .toArray(GrantedAuthority[]::new));
    }
}
