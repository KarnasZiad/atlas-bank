package ma.atlasbank.account.domain;

import java.util.Objects;

/**
 * Employee acting on an account. Four-eyes rules compare the immutable subject issued by the
 * identity provider, never the login.
 *
 * @param subject  immutable identifier at the identity provider
 * @param username login, kept for display and audit
 */
public record StaffMember(String subject, String username) {

    public StaffMember {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(username, "username");
    }
}
