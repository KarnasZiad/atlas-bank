package ma.atlasbank.notification.application.port;

import java.util.Optional;
import java.util.UUID;
import ma.atlasbank.notification.domain.Recipient;

public interface RecipientRepository {

    Recipient save(Recipient recipient);

    Optional<Recipient> findById(UUID customerId);
}
