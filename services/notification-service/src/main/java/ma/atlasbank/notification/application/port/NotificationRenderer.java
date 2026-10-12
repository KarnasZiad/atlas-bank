package ma.atlasbank.notification.application.port;

import java.util.List;
import java.util.Locale;
import ma.atlasbank.notification.domain.NotificationType;
import ma.atlasbank.notification.domain.RenderedMessage;

/**
 * Produces the localized subject and body of a notification. Arguments may contain
 * {@link ma.atlasbank.commons.money.Money} values, formatted according to the language.
 */
public interface NotificationRenderer {

    RenderedMessage render(NotificationType type, Locale language, List<Object> arguments);
}
