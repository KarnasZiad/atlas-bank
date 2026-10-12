package ma.atlasbank.customer.infrastructure.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class KeycloakAdminClientConfiguration {

    /**
     * Token management for calls made outside any user request (asynchronous listeners):
     * client-credentials tokens are obtained, cached and renewed by Spring Security.
     */
    @Bean
    OAuth2AuthorizedClientManager serviceAuthorizedClientManager(
            ClientRegistrationRepository clientRegistrations, OAuth2AuthorizedClientService authorizedClients) {
        return new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrations, authorizedClients);
    }

    @Bean
    RestClient keycloakAdminRestClient(
            RestClient.Builder builder,
            OAuth2AuthorizedClientManager serviceAuthorizedClientManager,
            KeycloakAdminProperties properties) {
        OAuth2ClientHttpRequestInterceptor serviceToken = new OAuth2ClientHttpRequestInterceptor(serviceAuthorizedClientManager);
        serviceToken.setClientRegistrationIdResolver(request -> properties.clientRegistrationId());
        // Always act with the service identity, never with the identity of a user being served.
        serviceToken.setPrincipalResolver(request -> null);
        return builder
                .baseUrl(properties.baseUrl().toString())
                .requestInterceptor(serviceToken)
                .build();
    }
}
