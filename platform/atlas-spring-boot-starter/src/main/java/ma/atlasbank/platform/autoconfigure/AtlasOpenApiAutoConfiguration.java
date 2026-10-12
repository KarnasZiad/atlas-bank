package ma.atlasbank.platform.autoconfigure;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.platform.config.AtlasProperties;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Describes every service API with the same metadata and the OAuth2 authorization-code flow
 * of the identity provider, so the aggregated API console at the gateway can sign users in.
 */
@AutoConfiguration(after = AtlasCoreAutoConfiguration.class)
@ConditionalOnClass(OpenAPI.class)
public class AtlasOpenApiAutoConfiguration {

    private static final String SECURITY_SCHEME = "atlas-oauth2";

    @Bean
    @ConditionalOnMissingBean
    OpenAPI atlasOpenApi(Environment environment, ObjectProvider<BuildProperties> buildProperties, AtlasProperties properties) {
        // The caller identity is resolved from the token, it is not an API parameter.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Requester.class);

        String serviceName = environment.getRequiredProperty("spring.application.name");
        String version = buildProperties.stream().map(BuildProperties::getVersion).findFirst().orElse("development");
        OAuthFlow authorizationCode = new OAuthFlow()
                .authorizationUrl(properties.openapi().authorizationUrl())
                .tokenUrl(properties.openapi().tokenUrl())
                .scopes(new Scopes().addString("openid", "OpenID Connect sign-in"));

        return new OpenAPI()
                .info(new Info().title("Atlas Bank - " + serviceName).version(version))
                .servers(List.of(new Server().url(properties.openapi().serverUrl()).description("API gateway")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(new OAuthFlows().authorizationCode(authorizationCode))))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
