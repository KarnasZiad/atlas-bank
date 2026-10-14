package ma.atlasbank.notification.domain;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * A value shown to the customer in their language (status, product, rejection reason), resolved
 * from the message bundles with the raw value as fallback.
 */
public record LocalizedLabel(String key, String fallback) {

    public static LocalizedLabel of(Enum<?> value) {
        return new LocalizedLabel("label." + value.getDeclaringClass().getSimpleName() + "." + value.name(), value.name());
    }

    public static LocalizedLabel rejection(String code) {
        return new LocalizedLabel("rejection." + code, code);
    }

    /** What changed, as one phrase: {@code label.ContactChanges.PHONE_NUMBER_AND_POSTAL_ADDRESS}. */
    public static LocalizedLabel contactChanges(Collection<? extends Enum<?>> changes) {
        String combination = changes.stream().map(Enum::name).sorted().collect(Collectors.joining("_AND_"));
        return new LocalizedLabel("label.ContactChanges." + combination, combination);
    }

    public static LocalizedLabel changedBy(boolean customer) {
        String who = customer ? "CUSTOMER" : "BANK";
        return new LocalizedLabel("label.ChangedBy." + who, who);
    }
}
