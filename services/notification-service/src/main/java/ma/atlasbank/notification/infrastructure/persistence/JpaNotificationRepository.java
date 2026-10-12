package ma.atlasbank.notification.infrastructure.persistence;

import java.util.UUID;
import ma.atlasbank.notification.application.port.NotificationRepository;
import ma.atlasbank.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaNotificationRepository extends JpaRepository<Notification, UUID>, NotificationRepository {
}
