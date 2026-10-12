package ma.atlasbank.platform.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Reads the trace identifier from the Micrometer tracer, when tracing is enabled.
 */
public class MicrometerTraceIdProvider implements TraceIdProvider {

    private final ObjectProvider<Tracer> tracer;

    public MicrometerTraceIdProvider(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    public Optional<String> currentTraceId() {
        return Optional.ofNullable(tracer.getIfAvailable())
                .map(Tracer::currentSpan)
                .map(Span::context)
                .map(TraceContext::traceId);
    }
}
