package ma.atlasbank.notification.infrastructure.persistence;

import java.util.UUID;
import ma.atlasbank.notification.application.port.RecipientRepository;
import ma.atlasbank.notification.domain.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaRecipientRepository extends JpaRepository<Recipient, UUID>, RecipientRepository {
}
