package ma.atlasbank.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Employee who approved or refused a staff order (four-eyes control). The immutable subject, not the
 * login, identifies the person, so a renamed account cannot pass for someone else.
 */
@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reviewer {

    @Column(name = "reviewer_subject", length = 64)
    private String subject;

    @Column(name = "reviewer_username", length = 100)
    private String username;
}
