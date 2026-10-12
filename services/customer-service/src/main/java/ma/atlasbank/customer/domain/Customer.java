package ma.atlasbank.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.error.OperationNotPermittedException;
import ma.atlasbank.platform.jpa.AuditableEntity;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

/**
 * Customer aggregate: identity, contact details and KYC lifecycle.
 *
 * <p>All state changes go through intention-revealing methods that guard the lifecycle,
 * so an invalid customer cannot be produced by any caller.
 */
@Getter
@Entity
@Audited
@Table(name = "customer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends AuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    /** Encrypted at rest by an auto-applied attribute converter. */
    @Column(name = "national_id", nullable = false, length = 512)
    private NationalId nationalId;

    /** Keyed hash of the national id: enables uniqueness checks without decrypting. */
    @NotAudited
    @Column(name = "national_id_fingerprint", nullable = false, unique = true, length = 64)
    private String nationalIdFingerprint;

    @Embedded
    private Address address;

    @Column(name = "preferred_language", nullable = false, length = 8)
    private Locale preferredLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CustomerStatus status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "registered_by", nullable = false, updatable = false, length = 100)
    private String registeredBy;

    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** Identifier of the sign-in account at the identity provider, once provisioned. */
    @Column(name = "identity_user_id", length = 64)
    private String identityUserId;

    public static Customer register(PersonalDetails details, ContactDetails contact, String nationalIdFingerprint,
                                    String registeredBy) {
        Customer customer = new Customer();
        customer.firstName = details.firstName();
        customer.lastName = details.lastName();
        customer.dateOfBirth = details.dateOfBirth();
        customer.nationalId = details.nationalId();
        customer.preferredLanguage = details.preferredLanguage();
        customer.nationalIdFingerprint = Objects.requireNonNull(nationalIdFingerprint);
        customer.email = contact.email();
        customer.phoneNumber = contact.phoneNumber();
        customer.address = contact.address();
        customer.registeredBy = Objects.requireNonNull(registeredBy);
        customer.status = CustomerStatus.PENDING_VERIFICATION;
        return customer;
    }

    /**
     * KYC approval under the four-eyes principle: the employee who registered the customer
     * cannot be the one who approves the file.
     */
    public void verify(String verifier, Instant verifiedAt) {
        if (status != CustomerStatus.PENDING_VERIFICATION) {
            throw invalidTransition(CustomerStatus.ACTIVE);
        }
        if (registeredBy.equals(verifier)) {
            throw new OperationNotPermittedException(CustomerErrorCode.SELF_APPROVAL_FORBIDDEN,
                    "A customer file must be verified by a different employee than the one who registered it");
        }
        transitionTo(CustomerStatus.ACTIVE, null);
        this.verifiedBy = verifier;
        this.verifiedAt = verifiedAt;
    }

    public void suspend(String reason) {
        transitionTo(CustomerStatus.SUSPENDED, reason);
    }

    public void reinstate() {
        if (status != CustomerStatus.SUSPENDED) {
            throw invalidTransition(CustomerStatus.ACTIVE);
        }
        transitionTo(CustomerStatus.ACTIVE, null);
    }

    public void close(String reason) {
        transitionTo(CustomerStatus.CLOSED, reason);
    }

    public void updateContact(String phoneNumber, Address address) {
        requireNotClosed();
        this.phoneNumber = Objects.requireNonNull(phoneNumber);
        this.address = Objects.requireNonNull(address);
    }

    public void linkIdentity(String identityUserId) {
        this.identityUserId = Objects.requireNonNull(identityUserId);
    }

    public boolean isIdentityProvisioned() {
        return identityUserId != null;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    private void transitionTo(CustomerStatus target, String reason) {
        if (!status.canTransitionTo(target)) {
            throw invalidTransition(target);
        }
        this.status = target;
        this.statusReason = reason;
    }

    private void requireNotClosed() {
        if (status == CustomerStatus.CLOSED) {
            throw new BusinessRuleViolationException(CustomerErrorCode.CUSTOMER_CLOSED, "Customer %s is closed", id);
        }
    }

    private BusinessRuleViolationException invalidTransition(CustomerStatus target) {
        return new BusinessRuleViolationException(CustomerErrorCode.INVALID_STATUS_TRANSITION,
                "Customer cannot go from %s to %s", status, target);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Customer customer && id != null && id.equals(customer.id));
    }

    @Override
    public int hashCode() {
        return Customer.class.hashCode();
    }
}
