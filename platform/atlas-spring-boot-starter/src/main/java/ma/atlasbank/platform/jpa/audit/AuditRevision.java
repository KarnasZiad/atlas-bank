package ma.atlasbank.platform.jpa.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Envers revision enriched with the actor responsible for the change, so that the audit
 * history answers "who changed what, and when" without joining any other system.
 */
@Getter
@Entity
@Table(name = "audit_revision")
@RevisionEntity(AuditRevisionListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditRevision {

    @Id
    @RevisionNumber
    @SequenceGenerator(name = "audit_revision_seq", sequenceName = "audit_revision_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_revision_seq")
    private Long id;

    @RevisionTimestamp
    @Column(name = "revision_timestamp", nullable = false)
    private long timestamp;

    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    public Instant revisionInstant() {
        return Instant.ofEpochMilli(timestamp);
    }

    void assignActor(String actor) {
        this.actor = actor;
    }
}
