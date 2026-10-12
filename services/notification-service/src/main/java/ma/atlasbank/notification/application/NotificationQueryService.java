package ma.atlasbank.notification.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.notification.application.port.NotificationRepository;
import ma.atlasbank.notification.application.view.NotificationView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notifications;
    private final NotificationViewMapper mapper;

    /**
     * Customers read their own notifications; staff may filter on any customer or see them all.
     */
    public Page<NotificationView> list(UUID customerId, Requester requester, Pageable pageable) {
        UUID scope = requester.isStaff() ? customerId : requester.requireCustomerId();
        return (scope == null ? notifications.findAll(pageable) : notifications.findByCustomerId(scope, pageable))
                .map(mapper::toView);
    }
}
