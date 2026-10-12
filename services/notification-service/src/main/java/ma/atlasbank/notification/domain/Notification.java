package ma.atlasbank.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**
 * A message delivered to a customer. Recorded once per (source event, customer, type), which
 * makes redelivered events harmless.
 */
@Getter
@Entity
@Immutable
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false, length = 20)
    private Channel channel;

    @Column(name = "source_event_id", nullable = false, updatable = false)
    private UUID sourceEventId;

    @Column(name = "destination", nullable = false, updatable = false, length = 254)
    private String destination;

    @Column(name = "subject", nullable = false, updatable = false, length = 200)
    private String subject;

    @Column(name = "body", nullable = false, updatable = false, length = 4000)
    private String body;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    public static Notification sent(UUID customerId, NotificationType type, Channel channel, UUID sourceEventId,
                                    String destination, RenderedMessage message, Instant sentAt) {
        Notification notification = new Notification();
        notification.customerId = customerId;
        notification.type = type;
        notification.channel = channel;
        notification.sourceEventId = sourceEventId;
        notification.destination = destination;
        notification.subject = message.subject();
        notification.body = message.body();
        notification.sentAt = sentAt;
        return notification;
    }
}
