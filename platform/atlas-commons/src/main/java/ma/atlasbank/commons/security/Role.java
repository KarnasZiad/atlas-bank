package ma.atlasbank.commons.security;

/**
 * Realm roles issued by the identity provider. {@code ADMIN} implies {@code EMPLOYEE}
 * (the hierarchy is configured once, in the platform starter).
 */
public enum Role {
    CUSTOMER,
    EMPLOYEE,
    ADMIN
}
