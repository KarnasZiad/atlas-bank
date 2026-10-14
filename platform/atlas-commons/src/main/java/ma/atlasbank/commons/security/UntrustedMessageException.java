package ma.atlasbank.commons.security;

/**
 * A message failed its authenticity check (missing, unknown or invalid signature).
 *
 * <p>It is a security event, not a business rejection: nothing is recorded for the message (a forger
 * must not be able to "consume" the identifier of a genuine one), it is never retried and lands on
 * the dead-letter topic for investigation.
 */
public class UntrustedMessageException extends RuntimeException {

    public UntrustedMessageException(String message) {
        super(message);
    }
}
