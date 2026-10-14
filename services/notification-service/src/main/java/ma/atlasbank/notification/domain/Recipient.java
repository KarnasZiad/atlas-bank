package ma.atlasbank.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Local projection of the contact details of a customer, fed by customer snapshots, so that
 * notifications never require a synchronous call to the customer service.
 *
 * <p>Once the relationship is closed the contact details are erased: the bank no longer writes to the
 * customer through this channel, and keeps no copy it does not need.
 */
@Getter
@Entity
@Table(name = "recipient")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipient {

    @Id
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "full_name", length = 201)
    private String fullName;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "language", length = 8)
    private Locale language;

    @Column(name = "snapshot_version", nullable = false)
    private long snapshotVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "erased_at")
    private Instant erasedAt;

    public static Recipient of(UUID customerId, ContactCard card, long version, Instant at) {
        Recipient recipient = blank(customerId);
        recipient.update(card, version, at);
        return recipient;
    }

    /** The first snapshot received for this customer already closes the relationship. */
    public static Recipient erased(UUID customerId, long version, Instant at) {
        Recipient recipient = blank(customerId);
        recipient.erase(version, at);
        return recipient;
    }

    /**
     * @return whether the snapshot was newer than the projection and has been applied
     */
    public boolean update(ContactCard card, long version, Instant at) {
        if (version <= snapshotVersion || isErased()) {
            return false;
        }
        this.fullName = card.fullName();
        this.email = card.email();
        this.phoneNumber = card.phoneNumber();
        this.language = card.language();
        this.snapshotVersion = version;
        this.updatedAt = at;
        return true;
    }

    /**
     * @return whether the snapshot was newer than the projection and has been applied
     */
    public boolean erase(long version, Instant at) {
        if (version <= snapshotVersion) {
            return false;
        }
        this.fullName = null;
        this.email = null;
        this.phoneNumber = null;
        this.language = null;
        this.snapshotVersion = version;
        this.updatedAt = at;
        this.erasedAt = at;
        return true;
    }

    public boolean isErased() {
        return erasedAt != null;
    }

    private static Recipient blank(UUID customerId) {
        Recipient recipient = new Recipient();
        recipient.customerId = customerId;
        recipient.snapshotVersion = -1;
        return recipient;
    }

    public record ContactCard(String fullName, String email, String phoneNumber, Locale language) {
    }
}
