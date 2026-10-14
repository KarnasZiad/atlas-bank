package ma.atlasbank.customer.infrastructure.identity;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ma.atlasbank.customer.application.port.IdentityProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Customer sign-in accounts managed through the Keycloak administration REST API.
 *
 * <p>The account carries the {@code customer_id} attribute (mapped into access tokens) and joins
 * the customers group (which grants the CUSTOMER role). No password is ever set by the bank:
 * Keycloak e-mails the customer a link to verify the address and choose one.
 */
@Slf4j
@Component
class KeycloakIdentityProvider implements IdentityProvider {

    private static final ParameterizedTypeReference<List<KeycloakUser>> USER_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient keycloak;
    private final KeycloakAdminProperties properties;

    KeycloakIdentityProvider(RestClient keycloakAdminRestClient, KeycloakAdminProperties properties) {
        this.keycloak = keycloakAdminRestClient;
        this.properties = properties;
    }

    @Override
    public String provisionCustomerAccount(CustomerAccount account) {
        String accountId = create(account).orElseGet(() -> findExisting(account));
        requestFirstSignInActions(accountId);
        return accountId;
    }

    @Override
    public Optional<String> findCustomerAccount(CustomerAccount account) {
        return userNamed(account.email())
                .filter(existing -> existing.attribute(properties.customerIdAttribute()).contains(account.customerId().toString()))
                .map(KeycloakUser::id);
    }

    @Override
    public void enableAccount(String accountId) {
        update(accountId, KeycloakUser.enabled(true));
    }

    @Override
    public void disableAccount(String accountId) {
        update(accountId, KeycloakUser.enabled(false));
    }

    /**
     * @return the new account id, or empty when an account already exists for this e-mail
     */
    private Optional<String> create(CustomerAccount account) {
        KeycloakUser user = new KeycloakUser(
                null,
                account.email(),
                account.email(),
                account.firstName(),
                account.lastName(),
                true,
                false,
                Map.of(properties.customerIdAttribute(), List.of(account.customerId().toString()),
                        "locale", List.of(account.language().toLanguageTag())),
                List.of(properties.customerGroup()),
                properties.requiredActions());
        return keycloak.post()
                .uri("/admin/realms/{realm}/users", properties.realm())
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                        return Optional.empty();
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IdentityProviderException("Account creation rejected with status " + response.getStatusCode());
                    }
                    return Optional.of(idFromLocation(response.getHeaders().getLocation()));
                });
    }

    /**
     * Idempotent retry path: the account exists, but it must belong to this very customer.
     */
    private String findExisting(CustomerAccount account) {
        KeycloakUser existing = userNamed(account.email())
                .orElseThrow(() -> new IdentityProviderException("Account reported as existing but not found"));
        if (!existing.attribute(properties.customerIdAttribute()).contains(account.customerId().toString())) {
            throw new IdentityProviderException("An account with this username belongs to another person");
        }
        return existing.id();
    }

    private Optional<KeycloakUser> userNamed(String username) {
        List<KeycloakUser> matches = keycloak.get()
                .uri("/admin/realms/{realm}/users?username={username}&exact=true&briefRepresentation=false",
                        properties.realm(), username)
                .retrieve()
                .body(USER_LIST);
        return Optional.ofNullable(matches).orElse(List.of()).stream().findFirst();
    }

    private void requestFirstSignInActions(String accountId) {
        try {
            keycloak.put()
                    .uri("/admin/realms/{realm}/users/{id}/execute-actions-email?lifespan={lifespan}",
                            properties.realm(), accountId, properties.actionsEmailLifespan().toSeconds())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(properties.requiredActions())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException mailFailure) {
            // The account is usable once the e-mail can be resent: do not undo the provisioning.
            log.warn("Account {} created but the activation e-mail could not be sent", accountId, mailFailure);
        }
    }

    private void update(String accountId, KeycloakUser changes) {
        keycloak.put()
                .uri("/admin/realms/{realm}/users/{id}", properties.realm(), accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(changes)
                .retrieve()
                .toBodilessEntity();
    }

    private static String idFromLocation(URI location) {
        if (location == null) {
            throw new IdentityProviderException("Account created without a Location header");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
