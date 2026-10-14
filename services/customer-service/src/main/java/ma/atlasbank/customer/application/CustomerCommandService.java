package ma.atlasbank.customer.application;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.error.ConflictException;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.customer.application.command.RegisterCustomerCommand;
import ma.atlasbank.customer.application.command.UpdateContactCommand;
import ma.atlasbank.customer.application.port.CustomerRepository;
import ma.atlasbank.customer.application.port.NationalIdFingerprinter;
import ma.atlasbank.customer.application.port.PhoneNumberNormalizer;
import ma.atlasbank.customer.application.view.CustomerView;
import ma.atlasbank.customer.domain.ContactDetails;
import ma.atlasbank.customer.domain.ContactField;
import ma.atlasbank.customer.domain.Customer;
import ma.atlasbank.customer.domain.CustomerErrorCode;
import ma.atlasbank.customer.domain.NationalId;
import ma.atlasbank.customer.domain.PersonalDetails;
import ma.atlasbank.customer.domain.StaffMember;
import ma.atlasbank.platform.time.BusinessCalendar;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer lifecycle use cases. Every change publishes a fresh snapshot through the
 * transactional outbox, atomically with the change itself.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CustomerCommandService {

    private final CustomerRepository customers;
    private final CustomerFinder finder;
    private final NationalIdFingerprinter fingerprinter;
    private final PhoneNumberNormalizer phoneNumbers;
    private final CustomerSettings settings;
    private final BusinessCalendar calendar;
    private final CustomerViewMapper mapper;
    private final CustomerSnapshotFactory snapshots;
    private final ApplicationEventPublisher events;

    public CustomerView register(RegisterCustomerCommand command, Requester requester) {
        PersonalDetails details = new PersonalDetails(
                command.firstName(),
                command.lastName(),
                command.dateOfBirth(),
                new NationalId(command.nationalId()),
                language(command.preferredLanguage()));
        requireLegalAge(details);
        ContactDetails contact = new ContactDetails(
                command.email(), phoneNumbers.toE164(command.phoneNumber()), command.address().toAddress());

        if (customers.existsByEmail(contact.email())) {
            throw new ConflictException(CustomerErrorCode.EMAIL_ALREADY_REGISTERED,
                    "A customer is already registered with this e-mail address");
        }
        String fingerprint = fingerprinter.fingerprint(details.nationalId());
        if (customers.existsByNationalIdFingerprint(fingerprint)) {
            throw new ConflictException(CustomerErrorCode.NATIONAL_ID_ALREADY_REGISTERED,
                    "A customer is already registered with this national identity number");
        }
        return saveAndPublish(Customer.register(details, contact, fingerprint, staffMember(requester)));
    }

    public CustomerView verify(UUID customerId, Requester requester) {
        Customer customer = finder.getById(customerId);
        customer.verify(staffMember(requester), calendar.now());
        return saveAndPublish(customer);
    }

    public CustomerView suspend(UUID customerId, String reason, Requester requester) {
        Customer customer = finder.getById(customerId);
        customer.suspend(reason, staffMember(requester));
        return saveAndPublish(customer);
    }

    public CustomerView reinstate(UUID customerId, Requester requester) {
        Customer customer = finder.getById(customerId);
        customer.reinstate(staffMember(requester));
        return saveAndPublish(customer);
    }

    public CustomerView close(UUID customerId, String reason) {
        Customer customer = finder.getById(customerId);
        customer.close(reason);
        return saveAndPublish(customer);
    }

    public CustomerView updateContact(UUID customerId, UpdateContactCommand command, Requester requester) {
        Customer customer = finder.getVisibleTo(customerId, requester);
        Set<ContactField> changes = customer.updateContact(
                phoneNumbers.toE164(command.phoneNumber()), command.address().toAddress());
        saveAndPublish(customer);
        if (!changes.isEmpty()) {
            events.publishEvent(snapshots.contactChanged(customer, changes, requester));
        }
        return mapper.toViewFor(customer, requester);
    }

    private static StaffMember staffMember(Requester requester) {
        return new StaffMember(requester.subject(), requester.username());
    }

    private CustomerView saveAndPublish(Customer customer) {
        Customer saved = customers.saveAndFlush(customer);
        events.publishEvent(snapshots.snapshotOf(saved));
        return mapper.toView(saved);
    }

    private void requireLegalAge(PersonalDetails details) {
        if (details.ageOn(calendar.today()) < settings.minimumAge()) {
            throw new BusinessRuleViolationException(CustomerErrorCode.CUSTOMER_UNDERAGE,
                    "Customers must be at least %d years old", settings.minimumAge());
        }
    }

    private Locale language(String requested) {
        String tag = requested == null || requested.isBlank() ? settings.defaultLanguage() : requested.strip();
        Locale language = Locale.forLanguageTag(tag);
        if (!settings.supportedLanguages().contains(language.getLanguage())) {
            throw new BusinessRuleViolationException(CustomerErrorCode.UNSUPPORTED_LANGUAGE,
                    "Language '%s' is not supported, expected one of %s", tag, settings.supportedLanguages());
        }
        return language;
    }
}
