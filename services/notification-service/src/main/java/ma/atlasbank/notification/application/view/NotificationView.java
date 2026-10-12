package ma.atlasbank.notification.application.view;

import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.notification.domain.Channel;
import ma.atlasbank.notification.domain.NotificationType;

public record NotificationView(
        UUID id,
        UUID customerId,
        NotificationType type,
        Channel channel,
        String destination,
        String subject,
        String body,
        Instant sentAt) {
}
