package ma.atlasbank.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Local projection of a customer, fed by the customer snapshots. It lets the ledger decide
 * whether accounts may be opened without calling the customer service at request time.
 */
@Getter
@Entity
@Table(name = "account_holder")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountHolder {

    @Id
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private HolderStatus status;

    /** Version of the latest snapshot applied: older or replayed snapshots are ignored. */
    @Column(name = "snapshot_version", nullable = false)
    private long snapshotVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AccountHolder of(UUID customerId, HolderStatus status, long snapshotVersion, Instant at) {
        AccountHolder holder = new AccountHolder();
        holder.customerId = customerId;
        holder.status = status;
        holder.snapshotVersion = snapshotVersion;
        holder.updatedAt = at;
        return holder;
    }

    /**
     * @return whether the snapshot was newer and has been applied
     */
    public boolean apply(HolderStatus newStatus, long version, Instant at) {
        if (version <= snapshotVersion) {
            return false;
        }
        this.status = newStatus;
        this.snapshotVersion = version;
        this.updatedAt = at;
        return true;
    }

    public boolean canOpenAccounts() {
        return status == HolderStatus.ACTIVE;
    }

    public boolean isBlocked() {
        return status == HolderStatus.SUSPENDED || status == HolderStatus.CLOSED;
    }
}
