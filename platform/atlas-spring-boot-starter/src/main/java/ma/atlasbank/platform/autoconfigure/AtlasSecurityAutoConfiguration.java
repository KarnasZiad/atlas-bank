package ma.atlasbank.platform.autoconfigure;

import ma.atlasbank.commons.security.Role;
import ma.atlasbank.platform.config.AtlasProperties;
import ma.atlasbank.platform.security.RequesterResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Zero-trust defaults for every service: each one validates the bearer token itself (issuer,
 * audience, signature, expiry) even though the gateway already did, and every endpoint
 * requires authentication unless explicitly listed as public.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class AtlasSecurityAutoConfiguration {

    private static final String DEFAULT_ROLE_PREFIX = "ROLE_";

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain atlasSecurityFilterChain(HttpSecurity http, AtlasProperties properties) throws Exception {
        String[] publicPaths = properties.security().publicPaths().toArray(String[]::new);
        return http
                // Stateless API authenticated by bearer tokens: no cookie, hence no CSRF exposure.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .build();
    }

    /**
     * ADMIN inherits every EMPLOYEE permission, declared once instead of repeated on endpoints.
     */
    @Bean
    @ConditionalOnMissingBean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(Role.ADMIN.name()).implies(Role.EMPLOYEE.name())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    @Bean
    @ConditionalOnMissingBean
    RequesterResolver requesterResolver(
            AtlasProperties properties,
            RoleHierarchy roleHierarchy,
            ObjectProvider<GrantedAuthorityDefaults> authorityDefaults) {
        String rolePrefix = authorityDefaults.getIfAvailable(() -> new GrantedAuthorityDefaults(DEFAULT_ROLE_PREFIX)).getRolePrefix();
        return new RequesterResolver(properties.security().customerIdClaim(), rolePrefix, roleHierarchy);
    }
}
