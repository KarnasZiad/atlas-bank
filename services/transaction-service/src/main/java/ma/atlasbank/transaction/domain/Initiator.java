package ma.atlasbank.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Who placed the order: the identity is captured at intake and travels with the order to the
 * ledger, which re-checks the initiator rights on its own.
 */
@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Initiator {

    @Column(name = "initiator_subject", nullable = false, updatable = false, length = 64)
    private String subject;

    @Column(name = "initiator_username", nullable = false, updatable = false, length = 100)
    private String username;

    @Column(name = "initiator_customer_id", updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "initiator_type", nullable = false, updatable = false, length = 20)
    private InitiatorType type;
}
