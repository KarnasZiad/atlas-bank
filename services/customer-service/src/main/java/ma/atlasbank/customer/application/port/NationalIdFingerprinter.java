package ma.atlasbank.customer.application.port;

import ma.atlasbank.customer.domain.NationalId;

/**
 * Produces a deterministic keyed fingerprint of a national id, so duplicates can be detected
 * while the value itself stays encrypted with a random IV.
 */
public interface NationalIdFingerprinter {

    String fingerprint(NationalId nationalId);
}
