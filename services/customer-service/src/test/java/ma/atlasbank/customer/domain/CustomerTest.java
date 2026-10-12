package ma.atlasbank.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.error.DomainException;
import ma.atlasbank.commons.error.OperationNotPermittedException;
import org.junit.jupiter.api.Test;

class CustomerTest {

    private static final String REGISTRAR = "agent.amina";
    private static final String REVIEWER = "agent.youssef";
    private static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");

    @Test
    void startsPendingVerification() {
        Customer customer = newCustomer();

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.PENDING_VERIFICATION);
        assertThat(customer.getRegisteredBy()).isEqualTo(REGISTRAR);
        assertThat(customer.getStatus().allowsSignIn()).isFalse();
    }

    @Test
    void isActivatedByAnotherEmployee() {
        Customer customer = newCustomer();

        customer.verify(REVIEWER, NOW);

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customer.getVerifiedBy()).isEqualTo(REVIEWER);
        assertThat(customer.getVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void enforcesTheFourEyesPrinciple() {
        Customer customer = newCustomer();

        assertThatThrownBy(() -> customer.verify(REGISTRAR, NOW))
                .isInstanceOf(OperationNotPermittedException.class)
                .extracting(error -> ((DomainException) error).errorCode())
                .isEqualTo(CustomerErrorCode.SELF_APPROVAL_FORBIDDEN);
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.PENDING_VERIFICATION);
    }

    @Test
    void cannotBeVerifiedTwice() {
        Customer customer = activeCustomer();

        assertThatThrownBy(() -> customer.verify("agent.other", NOW))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void suspensionAndReinstatementFollowTheLifecycle() {
        Customer customer = activeCustomer();

        customer.suspend("Suspicious activity");
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.SUSPENDED);
        assertThat(customer.getStatusReason()).isEqualTo("Suspicious activity");

        customer.reinstate();
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customer.getStatusReason()).isNull();
    }

    @Test
    void onlySuspendedCustomersCanBeReinstated() {
        Customer customer = newCustomer();

        assertThatThrownBy(customer::reinstate).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void closureIsFinal() {
        Customer customer = activeCustomer();
        customer.close("Customer request");

        assertThatThrownBy(() -> customer.suspend("any")).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> customer.updateContact("+212600000000", address()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void masksTheNationalIdentityNumber() {
        NationalId nationalId = new NationalId(" ab123456 ");

        assertThat(nationalId.value()).isEqualTo("AB123456");
        assertThat(nationalId.masked()).isEqualTo("******56");
        assertThat(nationalId).hasToString("******56");
    }

    @Test
    void rejectsMalformedNationalIdentityNumbers() {
        assertThatThrownBy(() -> new NationalId("123-ABC")).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void rejectsUnknownCountries() {
        assertThatThrownBy(() -> new Address("1 rue X", "Rabat", "10000", "XX"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    private static Customer activeCustomer() {
        Customer customer = newCustomer();
        customer.verify(REVIEWER, NOW);
        return customer;
    }

    private static Customer newCustomer() {
        PersonalDetails details = new PersonalDetails(
                "Salma", "Benali", LocalDate.of(1990, 5, 17), new NationalId("AB123456"), Locale.FRENCH);
        ContactDetails contact = new ContactDetails("Salma.Benali@example.ma", "+212612345678", address());
        return Customer.register(details, contact, "fingerprint", REGISTRAR);
    }

    private static Address address() {
        return new Address("12 avenue Mohammed V", "Rabat", "10000", "ma");
    }
}
