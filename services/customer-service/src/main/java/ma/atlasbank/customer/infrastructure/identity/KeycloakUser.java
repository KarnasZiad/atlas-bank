package ma.atlasbank.customer.infrastructure.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Subset of Keycloak's {@code UserRepresentation} used by the bank. Null members are omitted, so
 * the same record serves full creations and partial updates.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
record KeycloakUser(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean enabled,
        Boolean emailVerified,
        Map<String, List<String>> attributes,
        List<String> groups,
        List<String> requiredActions) {

    static KeycloakUser enabled(boolean enabled) {
        return new KeycloakUser(null, null, null, null, null, enabled, null, null, null, null);
    }

    List<String> attribute(String name) {
        return attributes == null ? List.of() : attributes.getOrDefault(name, List.of());
    }
}
