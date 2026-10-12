package ma.atlasbank.notification.infrastructure.rendering;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import ma.atlasbank.commons.money.Money;
import ma.atlasbank.notification.application.port.NotificationRenderer;
import ma.atlasbank.notification.domain.LocalizedLabel;
import ma.atlasbank.notification.domain.NotificationType;
import ma.atlasbank.notification.domain.RenderedMessage;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Templates live in {@code messages/notifications_<lang>.properties} (Spring {@link MessageSource},
 * falling back to the default bundle), so wording changes and new languages need no code change.
 * Amounts and labels are rendered with the conventions of the recipient language.
 */
@Component
class MessageSourceNotificationRenderer implements NotificationRenderer {

    private static final String KEY_PREFIX = "notification.";

    private final MessageSource messages;

    MessageSourceNotificationRenderer(MessageSource messages) {
        this.messages = messages;
    }

    @Override
    public RenderedMessage render(NotificationType type, Locale language, List<Object> arguments) {
        Object[] localized = arguments.stream().map(argument -> localize(argument, language)).toArray();
        String key = KEY_PREFIX + type.name();
        return new RenderedMessage(
                messages.getMessage(key + ".subject", localized, language),
                messages.getMessage(key + ".body", localized, language));
    }

    private Object localize(Object argument, Locale language) {
        return switch (argument) {
            case Money money -> {
                NumberFormat format = NumberFormat.getCurrencyInstance(language);
                format.setCurrency(money.currency());
                yield format.format(money.amount());
            }
            case LocalizedLabel label -> messages.getMessage(label.key(), null, label.fallback(), language);
            default -> argument;
        };
    }
}
