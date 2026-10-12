package ma.atlasbank.notification.domain;

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
}
