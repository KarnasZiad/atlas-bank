package ma.atlasbank.customer.domain;

import java.util.Objects;

/**
 * Employee acting on a customer file. Four-eyes rules compare the immutable subject issued by the
 * identity provider, never the login, which an administrator can rename.
 *
 * @param subject  immutable identifier at the identity provider
 * @param username login, kept for display and audit
 */
public record StaffMember(String subject, String username) {

    public StaffMember {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(username, "username");
    }

    public boolean isSamePersonAs(String otherSubject) {
        return subject.equals(otherSubject);
    }
}
