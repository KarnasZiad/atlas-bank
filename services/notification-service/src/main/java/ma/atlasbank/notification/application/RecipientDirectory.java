package ma.atlasbank.notification.application;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.contracts.customer.CustomerSnapshot;
import ma.atlasbank.notification.application.port.RecipientRepository;
import ma.atlasbank.notification.domain.Recipient;
import ma.atlasbank.notification.domain.Recipient.ContactCard;
import ma.atlasbank.platform.time.BusinessCalendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the contact details of customers from their snapshots; stale snapshots are ignored.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RecipientDirectory {

    private final RecipientRepository recipients;
    private final BusinessCalendar calendar;

    public void apply(CustomerSnapshot snapshot) {
        ContactCard card = new ContactCard(
                snapshot.firstName() + " " + snapshot.lastName(),
                snapshot.email(),
                snapshot.phoneNumber(),
                Locale.forLanguageTag(snapshot.preferredLanguage()));
        recipients.findById(snapshot.customerId()).ifPresentOrElse(
                known -> known.update(card, snapshot.version(), calendar.now()),
                () -> recipients.save(Recipient.of(snapshot.customerId(), card, snapshot.version(), calendar.now())));
    }
}
