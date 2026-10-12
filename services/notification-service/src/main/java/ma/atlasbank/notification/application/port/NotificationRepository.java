package ma.atlasbank.notification.application.port;

import java.util.UUID;
import ma.atlasbank.notification.domain.Notification;
import ma.atlasbank.notification.domain.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationRepository {

    Notification save(Notification notification);

    boolean existsBySourceEventIdAndCustomerIdAndType(UUID sourceEventId, UUID customerId, NotificationType type);

    Page<Notification> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Notification> findAll(Pageable pageable);
}
