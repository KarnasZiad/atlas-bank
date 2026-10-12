package ma.atlasbank.platform.messaging;

import ma.atlasbank.platform.config.AtlasProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

/**
 * Periodically retries outbox entries that were not delivered. Consumers are idempotent, so a
 * duplicate delivery caused by concurrent resubmission from several instances is harmless.
 */
public class EventPublicationResubmitter {

    private static final Logger LOG = LoggerFactory.getLogger(EventPublicationResubmitter.class);

    private final IncompleteEventPublications incompletePublications;
    private final ResubmissionOptions options;

    public EventPublicationResubmitter(IncompleteEventPublications incompletePublications, AtlasProperties.Events settings) {
        this.incompletePublications = incompletePublications;
        this.options = ResubmissionOptions.defaults().withMinAge(settings.resubmissionDelay());
    }

    public void resubmitIncompletePublications() {
        try {
            incompletePublications.resubmitIncompletePublications(options);
        } catch (RuntimeException failure) {
            LOG.warn("Outbox resubmission failed, it will be retried on the next run", failure);
        }
    }
}
