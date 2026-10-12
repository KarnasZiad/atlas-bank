package ma.atlasbank.customer.infrastructure.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Access to the Keycloak administration API ({@code atlas.identity.keycloak.*}).
 *
 * @param baseUrl              internal URL of Keycloak
 * @param realm                realm holding the bank users
 * @param clientRegistrationId Spring Security client registration used to obtain service tokens
 * @param customerGroup        group granting the CUSTOMER role
 * @param customerIdAttribute  user attribute exposed as the {@code customer_id} token claim
 * @param requiredActions      actions the customer completes on first sign-in
 * @param actionsEmailLifespan validity of the link sent by e-mail
 */
@Validated
@ConfigurationProperties("atlas.identity.keycloak")
public record KeycloakAdminProperties(
        @NotNull URI baseUrl,
        @NotBlank String realm,
        @DefaultValue("keycloak-admin") @NotBlank String clientRegistrationId,
        @DefaultValue("/customers") @NotBlank String customerGroup,
        @DefaultValue("customer_id") @NotBlank String customerIdAttribute,
        @DefaultValue({"VERIFY_EMAIL", "UPDATE_PASSWORD"}) @NotEmpty List<String> requiredActions,
        @DefaultValue("24h") @NotNull Duration actionsEmailLifespan) {
}
