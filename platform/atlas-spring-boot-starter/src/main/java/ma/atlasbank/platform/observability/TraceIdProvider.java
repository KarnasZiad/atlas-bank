package ma.atlasbank.platform.observability;

import java.util.Optional;

/**
 * Exposes the identifier of the current distributed trace, if any.
 */
@FunctionalInterface
public interface TraceIdProvider {

    Optional<String> currentTraceId();
}
