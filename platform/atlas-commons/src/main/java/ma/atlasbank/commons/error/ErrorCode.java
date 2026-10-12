package ma.atlasbank.commons.error;

/**
 * Stable, machine-readable error identifier exposed to API clients.
 *
 * <p>Each service declares its codes as an enum implementing this interface, so the
 * code is the enum constant name and can never drift from the source.
 */
public interface ErrorCode {

    String name();

    default String code() {
        return name();
    }
}
